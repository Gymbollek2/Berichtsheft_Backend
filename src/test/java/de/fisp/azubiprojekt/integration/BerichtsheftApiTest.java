package de.fisp.azubiprojekt.integration;

import com.jayway.jsonpath.JsonPath;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.context.WebApplicationContext;

import java.nio.charset.StandardCharsets;
import java.time.DayOfWeek;
import java.time.LocalDate;
import java.util.List;

import static org.hamcrest.Matchers.*;
import static org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers.springSecurity;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/**
 * Testet die wichtigsten Ablaeufe ueber die echte REST-API inkl. Security und In-Memory-Datenbank.
 * Grundlage sind die Demo-Daten (ausbilder, azubi, azubi2).
 */
@SpringBootTest(properties = {
        "spring.datasource.url=jdbc:h2:mem:berichtsheft-test;DB_CLOSE_DELAY=-1",
        "spring.jpa.hibernate.ddl-auto=create-drop",
        "server.ssl.enabled=false",
        "app.mail.enabled=false",
        "app.demo-data.enabled=true",
        // Die Tests rechnen mit dem kleinen Demo-Lernzielplan; der Rahmenplan wird in RahmenplanImportTest geprueft
        "app.rahmenplan-import.enabled=false"
})
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_EACH_TEST_METHOD)
class BerichtsheftApiTest {

    @Autowired
    private WebApplicationContext context;

    private MockMvc mvc;

    @BeforeEach
    void setUp() {
        mvc = MockMvcBuilders.webAppContextSetup(context).apply(springSecurity()).build();
    }

    // ------------------------------------------------------------------ Hilfsmethoden

    private String login(String username, String password) throws Exception {
        String body = mvc.perform(post("/v1/passport/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"username\":\"" + username + "\",\"password\":\"" + password + "\"}"))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        return "Bearer " + JsonPath.read(body, "$.accessToken");
    }

    private String json(ResultActions actions) throws Exception {
        return actions.andReturn().getResponse().getContentAsString();
    }

    private Integer lernzielId(String token, String title) throws Exception {
        String all = json(mvc.perform(get("/v1/lernziel").header("Authorization", token)));
        List<Integer> ids = JsonPath.read(all, "$[?(@.title == '" + title + "')].id");
        return ids.get(0);
    }

    private ResultActions createLernziel(String token, String title, String kind, Integer parentId) throws Exception {
        return mvc.perform(post("/v1/lernziel").header("Authorization", token)
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"title\":\"" + title + "\",\"kind\":\"" + kind + "\",\"parentId\":" + parentId + "}"));
    }

    private ResultActions saveEintrag(String token, LocalDate date, double hours, String art, String text, String lernzielIds)
            throws Exception {
        return mvc.perform(put("/v1/berichtsheft/eintrag").header("Authorization", token)
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"date\":\"" + date + "\",\"hours\":" + hours + ",\"art\":\"" + art + "\","
                        + "\"description\":" + (text == null ? "null" : "\"" + text + "\"")
                        + ",\"lernzielIds\":[" + lernzielIds + "]}"));
    }

    // ------------------------------------------------------------------ Login & Rechte

    @Test
    void loginWithWrongPasswordIsRejected() throws Exception {
        mvc.perform(post("/v1/passport/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"username\":\"azubi\",\"password\":\"falsch\"}"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void requestsWithoutTokenAreRejected() throws Exception {
        mvc.perform(get("/v1/lernziel")).andExpect(status().isUnauthorized());
    }

    @Test
    void loginReturnsUserWithRoleAndComputedTrainingYear() throws Exception {
        mvc.perform(post("/v1/passport/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"username\":\"azubi\",\"password\":\"azubi123\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.accessToken", not(emptyString())))
                .andExpect(jsonPath("$.user.role").value("ROLE_TRAINEE"))
                .andExpect(jsonPath("$.user.yearOfTraining").value(2));
    }

    @Test
    void traineeCannotManagePersonsOrLernziele() throws Exception {
        String azubi = login("azubi", "azubi123");
        mvc.perform(get("/v1/person").header("Authorization", azubi)).andExpect(status().isForbidden());
        createLernziel(azubi, "X", "KIND_GENERAL", null).andExpect(status().isForbidden());
    }

    @Test
    void traineeCanOnlySeeOwnData() throws Exception {
        String azubi = login("azubi", "azubi123");
        Integer myId = JsonPath.read(json(mvc.perform(get("/v1/passport/me").header("Authorization", azubi))), "$.id");

        String trainer = login("ausbilder", "ausbilder123");
        List<Integer> ids = JsonPath.read(json(mvc.perform(get("/v1/person/azubis").header("Authorization", trainer))),
                "$[?(@.username == 'azubi2')].id");
        Integer otherId = ids.get(0);

        mvc.perform(get("/v1/personLernziel/person/" + myId).header("Authorization", azubi)).andExpect(status().isOk());
        mvc.perform(get("/v1/personLernziel/person/" + otherId).header("Authorization", azubi)).andExpect(status().isForbidden());
        mvc.perform(get("/v1/berichtsheft/woche").param("personId", String.valueOf(otherId)).header("Authorization", azubi))
                .andExpect(status().isForbidden());
    }

    // ------------------------------------------------------------------ Personen

    @Test
    void trainerCanManageAllRoles() throws Exception {
        String trainer = login("ausbilder", "ausbilder123");
        LocalDate start = LocalDate.now().minusMonths(1);
        mvc.perform(post("/v1/person").header("Authorization", trainer)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"firstName\":\"Jürgen\",\"lastName\":\"Neu\",\"role\":\"ROLE_TRAINEE\",\"password\":\"geheim123\","
                                + "\"beruf\":\"Fachinformatiker/in\",\"dateOfTrainingStart\":\"" + start + "\"}"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.username").value("juergen.neu"))
                .andExpect(jsonPath("$.yearOfTraining").value(1))
                // Ohne Anrede aus dem Vornamen vermutet, Beruf und Rolle ohne Gendern
                .andExpect(jsonPath("$.anrede").value("HERR"))
                .andExpect(jsonPath("$.beruf").value("Fachinformatiker"))
                .andExpect(jsonPath("$.roleLabel").value("Auszubildender"));

        mvc.perform(post("/v1/person").header("Authorization", trainer)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"anrede\":\"FRAU\",\"firstName\":\"Kim\",\"lastName\":\"Klar\",\"role\":\"ROLE_STUDENT\","
                                + "\"password\":\"geheim123\",\"beruf\":\"Wirtschaftsinformatiker/in\",\"dateOfTrainingStart\":\"" + start + "\"}"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.beruf").value("Wirtschaftsinformatikerin"))
                .andExpect(jsonPath("$.roleLabel").value("Duale Studentin"));

        // Neuer Azubi bekommt automatisch alle Lernziele zugewiesen und kann sich anmelden
        String neu = login("juergen.neu", "geheim123");
        mvc.perform(get("/v1/personLernziel/me").header("Authorization", neu))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(greaterThan(0))));

        mvc.perform(post("/v1/person").header("Authorization", trainer)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"firstName\":\"Bad\",\"lastName\":\"Admin\",\"role\":\"ROLE_ADMIN\",\"password\":\"geheim123\"}"))
                .andExpect(status().isBadRequest());

        // Ausbilder duerfen alles, auch weitere Ausbilder anlegen, bearbeiten und loeschen
        String neueAusbilderin = json(mvc.perform(post("/v1/person").header("Authorization", trainer)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"firstName\":\"Nora\",\"lastName\":\"Neu\",\"role\":\"ROLE_TRAINER\",\"password\":\"geheim123\"}"))
                .andExpect(status().isCreated()));
        Integer noraId = JsonPath.read(neueAusbilderin, "$.id");
        mvc.perform(put("/v1/person/" + noraId).header("Authorization", trainer)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"firstName\":\"Nora\",\"lastName\":\"Neumann\",\"role\":\"ROLE_TRAINER\",\"username\":\"nora.neu\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.lastName").value("Neumann"));
        String nora = login("nora.neu", "geheim123");
        mvc.perform(get("/v1/berichtsheft/offen").header("Authorization", nora)).andExpect(status().isOk());
        mvc.perform(delete("/v1/person/" + noraId).header("Authorization", trainer)).andExpect(status().is2xxSuccessful());
    }

    @Test
    void personRulesAreEnforced() throws Exception {
        String ausbilder = login("ausbilder", "ausbilder123");
        // Azubi ohne Ausbildungsbeginn
        mvc.perform(post("/v1/person").header("Authorization", ausbilder)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"firstName\":\"Ohne\",\"lastName\":\"Start\",\"role\":\"ROLE_TRAINEE\",\"password\":\"geheim123\"}"))
                .andExpect(status().isBadRequest());
        // Passwort ohne Ziffer
        mvc.perform(post("/v1/person").header("Authorization", ausbilder)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"firstName\":\"Neue\",\"lastName\":\"Ausbilderin\",\"role\":\"ROLE_TRAINER\",\"password\":\"nurbuchstaben\"}"))
                .andExpect(status().isBadRequest());
        // Ausbilder kann sich nicht selbst loeschen
        Integer ausbilderId = JsonPath.read(json(mvc.perform(get("/v1/passport/me").header("Authorization", ausbilder))), "$.id");
        mvc.perform(delete("/v1/person/" + ausbilderId).header("Authorization", ausbilder)).andExpect(status().isForbidden());
        // Azubi mit Berichtsheft-Eintraegen kann nicht zum Ausbilder werden
        List<Integer> ids = JsonPath.read(json(mvc.perform(get("/v1/person/azubis").header("Authorization", ausbilder))),
                "$[?(@.username == 'azubi')].id");
        mvc.perform(put("/v1/person/" + ids.get(0)).header("Authorization", ausbilder)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"firstName\":\"Max\",\"lastName\":\"Mustermann\",\"role\":\"ROLE_TRAINER\",\"username\":\"azubi\"}"))
                .andExpect(status().isBadRequest());
    }

    // ------------------------------------------------------------------ Lernziel-Hierarchie

    @Test
    void lernzielHierarchyRichtGrobFein() throws Exception {
        String trainer = login("ausbilder", "ausbilder123");
        Integer richt = lernzielId(trainer, "Software entwickeln");
        Integer grob = lernzielId(trainer, "Programmieren mit Java");

        // Richtlernziel darf kein uebergeordnetes Lernziel haben
        createLernziel(trainer, "Falsch 1", "KIND_GENERAL", richt).andExpect(status().isBadRequest());
        // Groblernziel braucht ein Richtlernziel
        createLernziel(trainer, "Falsch 2", "KIND_BROAD", null).andExpect(status().isBadRequest());
        createLernziel(trainer, "Falsch 3", "KIND_BROAD", grob).andExpect(status().isBadRequest());
        // Feinlernziel braucht ein Groblernziel
        createLernziel(trainer, "Falsch 4", "KIND_SPECIFIC", richt).andExpect(status().isBadRequest());
        createLernziel(trainer, "Streams und Lambdas", "KIND_SPECIFIC", grob).andExpect(status().isCreated());
        // Gleicher Titel auf gleicher Ebene
        createLernziel(trainer, "streams und lambdas", "KIND_SPECIFIC", grob).andExpect(status().isBadRequest());

        // Auswahl fuer uebergeordnete Lernziele passt zur Art
        mvc.perform(get("/v1/lernziel/parent-options").param("kind", "KIND_BROAD").header("Authorization", trainer))
                .andExpect(jsonPath("$[*].kind", everyItem(is("KIND_GENERAL"))))
                .andExpect(jsonPath("$", hasSize(2)));
        mvc.perform(get("/v1/lernziel/parent-options").param("kind", "KIND_GENERAL").header("Authorization", trainer))
                .andExpect(jsonPath("$", hasSize(0)));

        // Lernziele mit Unterzielen koennen weder geloescht noch in der Art geaendert werden
        mvc.perform(delete("/v1/lernziel/" + grob).header("Authorization", trainer)).andExpect(status().isBadRequest());
        mvc.perform(put("/v1/lernziel/" + grob).header("Authorization", trainer)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"title\":\"Programmieren mit Java\",\"kind\":\"KIND_SPECIFIC\",\"parentId\":" + grob + "}"))
                .andExpect(status().isBadRequest());
    }

    @Test
    void parentStatusIsDerivedFromChildren() throws Exception {
        String trainer = login("ausbilder", "ausbilder123");
        List<Integer> ids = JsonPath.read(json(mvc.perform(get("/v1/person/azubis").header("Authorization", trainer))),
                "$[?(@.username == 'azubi2')].id");
        String list = json(mvc.perform(get("/v1/personLernziel/person/" + ids.get(0)).header("Authorization", trainer)));
        Integer grob = JsonPath.<List<Integer>>read(list, "$[?(@.lernziel.title == 'Programmieren mit Java')].id").get(0);
        Integer oop = JsonPath.<List<Integer>>read(list, "$[?(@.lernziel.title == 'Objektorientierte Konzepte anwenden')].id").get(0);
        Integer junit = JsonPath.<List<Integer>>read(list, "$[?(@.lernziel.title == 'Unit-Tests mit JUnit schreiben')].id").get(0);

        // Status eines uebergeordneten Ziels ist nicht direkt setzbar
        mvc.perform(put("/v1/personLernziel/" + grob + "/status").header("Authorization", trainer)
                        .contentType(MediaType.APPLICATION_JSON).content("{\"status\":\"STATUS_DONE\"}"))
                .andExpect(status().isBadRequest());

        for (Integer id : List.of(oop, junit)) {
            mvc.perform(put("/v1/personLernziel/" + id + "/status").header("Authorization", trainer)
                            .contentType(MediaType.APPLICATION_JSON).content("{\"status\":\"STATUS_DONE\"}"))
                    .andExpect(status().isOk());
        }
        String after = json(mvc.perform(get("/v1/personLernziel/person/" + ids.get(0)).header("Authorization", trainer)));
        List<String> grobStatus = JsonPath.read(after, "$[?(@.lernziel.title == 'Programmieren mit Java')].status");
        List<String> richtStatus = JsonPath.read(after, "$[?(@.lernziel.title == 'Software entwickeln')].status");
        org.junit.jupiter.api.Assertions.assertEquals("STATUS_DONE", grobStatus.get(0));
        org.junit.jupiter.api.Assertions.assertEquals("STATUS_IN_PROGRESS", richtStatus.get(0));

        // Fortschritt zaehlt nur Lernziele ohne Unterziele (6 Feinlernziele im Demo-Plan), 2 erreicht = 33 %
        mvc.perform(get("/v1/personLernziel/person/" + ids.get(0) + "/fortschritt").header("Authorization", trainer))
                .andExpect(jsonPath("$.gesamt").value(6))
                .andExpect(jsonPath("$.erreicht").value(2))
                .andExpect(jsonPath("$.prozent").value(33));
    }

    // ------------------------------------------------------------------ Berichtsheft

    @Test
    void berichtsheftRulesAreEnforced() throws Exception {
        String azubi = login("azubi2", "azubi123");
        Integer grob = lernzielId(azubi, "Programmieren mit Java");
        LocalDate gestern = LocalDate.now().minusDays(1);

        saveEintrag(azubi, LocalDate.now().plusDays(1), 8, "BETRIEB", "Zukunft", "").andExpect(status().isBadRequest());
        saveEintrag(azubi, gestern, 11, "BETRIEB", "Zu lang", "").andExpect(status().isBadRequest());
        saveEintrag(azubi, gestern, 8, "BETRIEB", null, "").andExpect(status().isBadRequest());
        saveEintrag(azubi, gestern, 8, "BETRIEB", "Nur Groblernziel", String.valueOf(grob)).andExpect(status().isBadRequest());
        saveEintrag(azubi, gestern, 0, "KRANK", null, String.valueOf(grob)).andExpect(status().isBadRequest());
        saveEintrag(azubi, gestern, 0, "KRANK", null, "").andExpect(status().isOk());

        // Woche in der Zukunft kann noch nicht eingereicht werden
        mvc.perform(post("/v1/berichtsheft/woche/einreichen").header("Authorization", azubi)
                        .contentType(MediaType.APPLICATION_JSON).content("{\"datum\":\"" + LocalDate.now().plusDays(7) + "\"}"))
                .andExpect(status().isBadRequest());
    }

    @Test
    void fullBerichtsheftWorkflow() throws Exception {
        String azubi = login("azubi2", "azubi123");
        String trainer = login("ausbilder", "ausbilder123");
        LocalDate montag = LocalDate.now().with(DayOfWeek.MONDAY).minusWeeks(3);

        String lernziele = json(mvc.perform(get("/v1/personLernziel/me").header("Authorization", azubi)));
        Integer feinId = JsonPath.<List<Integer>>read(lernziele, "$[?(@.lernziel.kind == 'KIND_SPECIFIC')].lernziel.id").get(0);

        // 1. Montag mit Lernziel erfassen, Woche unvollstaendig -> Einreichen abgelehnt
        saveEintrag(azubi, montag, 8, "BETRIEB", "Tests geschrieben", String.valueOf(feinId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.lernziele", hasSize(1)));
        mvc.perform(post("/v1/berichtsheft/woche/einreichen").header("Authorization", azubi)
                        .contentType(MediaType.APPLICATION_JSON).content("{\"datum\":\"" + montag + "\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message", containsString("Dienstag")));

        // Lernziel ist dadurch "In Bearbeitung"
        mvc.perform(get("/v1/personLernziel/me").param("status", "STATUS_IN_PROGRESS").header("Authorization", azubi))
                .andExpect(jsonPath("$[*].lernziel.id", hasItem(feinId)));

        // 2. Rest der Woche erfassen und einreichen
        for (int i = 1; i < 5; i++) {
            saveEintrag(azubi, montag.plusDays(i), 8, "BETRIEB", "Tag " + i, "").andExpect(status().isOk());
        }
        String woche = json(mvc.perform(post("/v1/berichtsheft/woche/einreichen").header("Authorization", azubi)
                        .contentType(MediaType.APPLICATION_JSON).content("{\"datum\":\"" + montag.plusDays(2) + "\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("EINGEREICHT"))
                .andExpect(jsonPath("$.gesamtStunden").value(40.0)));
        Integer wochenId = JsonPath.read(woche, "$.id");

        // Eingereichte Woche ist gesperrt
        saveEintrag(azubi, montag.plusDays(1), 6, "SCHULE", "Berufsschule", "").andExpect(status().isBadRequest());

        // 3. Ausbilder gibt zurueck (Kommentar ist Pflicht)
        mvc.perform(get("/v1/berichtsheft/offen").header("Authorization", trainer))
                .andExpect(jsonPath("$[*].id", hasItem(wochenId)));
        mvc.perform(post("/v1/berichtsheft/woche/" + wochenId + "/ablehnen").header("Authorization", trainer)
                        .contentType(MediaType.APPLICATION_JSON).content("{\"kommentar\":\"\"}"))
                .andExpect(status().isBadRequest());
        mvc.perform(post("/v1/berichtsheft/woche/" + wochenId + "/ablehnen").header("Authorization", trainer)
                        .contentType(MediaType.APPLICATION_JSON).content("{\"kommentar\":\"Dienstag war Berufsschule\"}"))
                .andExpect(jsonPath("$.status").value("ABGELEHNT"));

        // 4. Azubi bessert nach, reicht erneut ein, Ausbilder zeichnet ab
        // Berufsschultage haben keine Lernziele
        saveEintrag(azubi, montag.plusDays(1), 6, "SCHULE", "Berufsschule: Datenbanken", String.valueOf(feinId))
                .andExpect(status().isBadRequest());
        saveEintrag(azubi, montag.plusDays(1), 6, "SCHULE", "Berufsschule: Datenbanken", "").andExpect(status().isOk());
        mvc.perform(post("/v1/berichtsheft/woche/einreichen").header("Authorization", azubi)
                        .contentType(MediaType.APPLICATION_JSON).content("{\"datum\":\"" + montag + "\"}"))
                .andExpect(jsonPath("$.status").value("EINGEREICHT"))
                // Die neue Pruefung steht noch aus: kein Pruefer/Pruefdatum, der Kommentar der Rueckgabe bleibt
                .andExpect(jsonPath("$.geprueftVon").value(nullValue()))
                .andExpect(jsonPath("$.geprueftAm").value(nullValue()))
                .andExpect(jsonPath("$.kommentar").value("Dienstag war Berufsschule"));
        mvc.perform(get("/v1/berichtsheft/historie").header("Authorization", azubi))
                .andExpect(jsonPath("$[?(@.id == " + wochenId + ")].geprueftAm", contains(nullValue())));
        mvc.perform(post("/v1/berichtsheft/woche/" + wochenId + "/abzeichnen").header("Authorization", trainer)
                        .contentType(MediaType.APPLICATION_JSON).content("{\"kommentar\":\"Passt\"}"))
                .andExpect(jsonPath("$.status").value("ABGEZEICHNET"))
                .andExpect(jsonPath("$.statusLabel").value("Abgenommen"))
                .andExpect(jsonPath("$.geprueftVon").value("Petra Pruefer"))
                .andExpect(jsonPath("$.gesamtStunden").value(38.0));

        // Azubis duerfen nicht abnehmen; in abgenommenen Wochen genutzte Lernziele sind geschuetzt
        mvc.perform(post("/v1/berichtsheft/woche/" + wochenId + "/abzeichnen").header("Authorization", azubi))
                .andExpect(status().isForbidden());
        mvc.perform(delete("/v1/lernziel/" + feinId).header("Authorization", trainer)).andExpect(status().isBadRequest());
    }

    // ------------------------------------------------------------------ CSV & Passwort

    @Test
    void lernzielCsvRoundTrip() throws Exception {
        String trainer = login("ausbilder", "ausbilder123");
        mvc.perform(multipart("/v1/lernziel/import")
                        .file(new MockMultipartFile("file", "lernziele.csv", "text/csv",
                                ("titel;beschreibung;art;uebergeordnetes_lernziel\n"
                                        + "Datenbanken;SQL und Modellierung;Groblernziel;Software entwickeln\n"
                                        + "\"Joins; Subqueries\";;Feinlernziel;Datenbanken\n"
                                        + "Falsche Ebene;;Feinlernziel;Software entwickeln\n"
                                        + "Kaputt;;Unbekannt;\n").getBytes(StandardCharsets.UTF_8)))
                        .header("Authorization", trainer))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.importiert").value(2))
                .andExpect(jsonPath("$.fehler", hasSize(2)));

        mvc.perform(get("/v1/lernziel/export").header("Authorization", trainer))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("\"Joins; Subqueries\"")));
    }

    @Test
    void passwordChangeInvalidatesOldToken() throws Exception {
        String alt = login("azubi", "azubi123");
        mvc.perform(post("/v1/passport/change-password").header("Authorization", alt)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"oldPassword\":\"azubi123\",\"newPassword\":\"neuesPasswort1\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.accessToken", not(emptyString())));

        mvc.perform(get("/v1/passport/me").header("Authorization", alt)).andExpect(status().isUnauthorized());
        login("azubi", "neuesPasswort1");
    }
}
