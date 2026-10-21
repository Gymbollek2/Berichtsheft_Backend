package de.fisp.azubiprojekt.integration;

import com.jayway.jsonpath.JsonPath;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.context.WebApplicationContext;

import java.time.DayOfWeek;
import java.time.LocalDate;

import static org.hamcrest.Matchers.*;
import static org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers.springSecurity;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Testet die wichtigsten Ablaeufe ueber die echte REST-API inkl. Security und In-Memory-Datenbank.
 * Grundlage sind die Demo-Daten (admin, ausbilder, azubi, azubi2).
 */
@SpringBootTest(properties = {
        "spring.datasource.url=jdbc:h2:mem:berichtsheft-test;DB_CLOSE_DELAY=-1",
        "spring.jpa.hibernate.ddl-auto=create-drop",
        "app.demo-data.enabled=true"
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

    private String login(String username, String password) throws Exception {
        String body = mvc.perform(post("/v1/passport/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"username\":\"" + username + "\",\"password\":\"" + password + "\"}"))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        return "Bearer " + JsonPath.read(body, "$.accessToken");
    }

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
    void loginReturnsUserWithRole() throws Exception {
        mvc.perform(post("/v1/passport/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"username\":\"ausbilder\",\"password\":\"ausbilder123\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.accessToken", not(emptyString())))
                .andExpect(jsonPath("$.user.role").value("ROLE_TRAINER"));
    }

    @Test
    void traineeCannotManagePersonsOrLernziele() throws Exception {
        String azubi = login("azubi", "azubi123");
        mvc.perform(get("/v1/person").header("Authorization", azubi)).andExpect(status().isForbidden());
        mvc.perform(post("/v1/lernziel").header("Authorization", azubi)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"title\":\"X\",\"kind\":\"KIND_BROAD\"}"))
                .andExpect(status().isForbidden());
    }

    @Test
    void traineeCanOnlySeeOwnData() throws Exception {
        String azubi = login("azubi", "azubi123");
        String meBody = mvc.perform(get("/v1/passport/me").header("Authorization", azubi))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
        Integer myId = JsonPath.read(meBody, "$.id");

        String trainer = login("ausbilder", "ausbilder123");
        String azubisBody = mvc.perform(get("/v1/person/azubis").header("Authorization", trainer))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
        java.util.List<Integer> ids = JsonPath.read(azubisBody, "$[?(@.username == 'azubi2')].id");
        Integer otherId = ids.get(0);

        mvc.perform(get("/v1/personLernziel/person/" + myId).header("Authorization", azubi)).andExpect(status().isOk());
        mvc.perform(get("/v1/personLernziel/person/" + otherId).header("Authorization", azubi)).andExpect(status().isForbidden());
        mvc.perform(get("/v1/berichtsheft/woche").param("personId", String.valueOf(otherId)).header("Authorization", azubi))
                .andExpect(status().isForbidden());
    }

    @Test
    void trainerCanCreateTraineeButNotAdmin() throws Exception {
        String trainer = login("ausbilder", "ausbilder123");
        mvc.perform(post("/v1/person").header("Authorization", trainer)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"firstName":"Jürgen","lastName":"Neu","role":"ROLE_TRAINEE","password":"geheim123",
                                 "beruf":"Fachinformatiker/in","yearOfTraining":1}
                                """))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.username").value("juergen.neu"));

        // Neuer Azubi bekommt automatisch alle Lernziele zugewiesen und kann sich anmelden
        String neu = login("juergen.neu", "geheim123");
        mvc.perform(get("/v1/personLernziel/me").header("Authorization", neu))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(greaterThan(0))));

        mvc.perform(post("/v1/person").header("Authorization", trainer)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"firstName\":\"Bad\",\"lastName\":\"Admin\",\"role\":\"ROLE_ADMIN\",\"password\":\"geheim123\",\"yearOfTraining\":0}"))
                .andExpect(status().isForbidden());
    }

    @Test
    void fullBerichtsheftWorkflow() throws Exception {
        String azubi = login("azubi2", "azubi123");
        String trainer = login("ausbilder", "ausbilder123");
        LocalDate montag = LocalDate.now().with(DayOfWeek.MONDAY).minusWeeks(3);

        String lernziele = mvc.perform(get("/v1/personLernziel/me").header("Authorization", azubi))
                .andReturn().getResponse().getContentAsString();
        Integer lernzielId = JsonPath.read(lernziele, "$[0].lernziel.id");

        // 1. Tageseintrag erfassen
        mvc.perform(put("/v1/berichtsheft/eintrag").header("Authorization", azubi)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"date\":\"" + montag + "\",\"hours\":8,\"art\":\"BETRIEB\","
                                + "\"description\":\"Tests geschrieben\",\"lernzielIds\":[" + lernzielId + "]}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.lernziele", hasSize(1)));

        // Lernziel ist dadurch "In Bearbeitung"
        mvc.perform(get("/v1/personLernziel/me").param("status", "STATUS_IN_PROGRESS").header("Authorization", azubi))
                .andExpect(jsonPath("$[*].lernziel.id", hasItem(lernzielId)));

        // 2. Woche einreichen
        String woche = mvc.perform(post("/v1/berichtsheft/woche/einreichen").header("Authorization", azubi)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"datum\":\"" + montag.plusDays(2) + "\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("EINGEREICHT"))
                .andExpect(jsonPath("$.gesamtStunden").value(8.0))
                .andReturn().getResponse().getContentAsString();
        Integer wochenId = JsonPath.read(woche, "$.id");

        // Eingereichte Woche ist gesperrt
        mvc.perform(put("/v1/berichtsheft/eintrag").header("Authorization", azubi)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"date\":\"" + montag.plusDays(1) + "\",\"hours\":8,\"art\":\"BETRIEB\"}"))
                .andExpect(status().isBadRequest());

        // 3. Ausbilder sieht die Woche in der Warteschlange und gibt sie zurueck
        mvc.perform(get("/v1/berichtsheft/offen").header("Authorization", trainer))
                .andExpect(jsonPath("$[*].id", hasItem(wochenId)));
        mvc.perform(post("/v1/berichtsheft/woche/" + wochenId + "/ablehnen").header("Authorization", trainer)
                        .contentType(MediaType.APPLICATION_JSON).content("{\"kommentar\":\"\"}"))
                .andExpect(status().isBadRequest());
        mvc.perform(post("/v1/berichtsheft/woche/" + wochenId + "/ablehnen").header("Authorization", trainer)
                        .contentType(MediaType.APPLICATION_JSON).content("{\"kommentar\":\"Bitte Dienstag ergaenzen\"}"))
                .andExpect(jsonPath("$.status").value("ABGELEHNT"));

        // 4. Azubi bessert nach und reicht erneut ein, Ausbilder zeichnet ab
        mvc.perform(put("/v1/berichtsheft/eintrag").header("Authorization", azubi)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"date\":\"" + montag.plusDays(1) + "\",\"hours\":6,\"art\":\"SCHULE\",\"description\":\"Berufsschule\"}"))
                .andExpect(status().isOk());
        mvc.perform(post("/v1/berichtsheft/woche/einreichen").header("Authorization", azubi)
                        .contentType(MediaType.APPLICATION_JSON).content("{\"datum\":\"" + montag + "\"}"))
                .andExpect(jsonPath("$.status").value("EINGEREICHT"));
        mvc.perform(post("/v1/berichtsheft/woche/" + wochenId + "/abzeichnen").header("Authorization", trainer)
                        .contentType(MediaType.APPLICATION_JSON).content("{\"kommentar\":\"Passt\"}"))
                .andExpect(jsonPath("$.status").value("ABGEZEICHNET"))
                .andExpect(jsonPath("$.geprueftVon").value("Petra Pruefer"))
                .andExpect(jsonPath("$.gesamtStunden").value(14.0));

        // Azubis duerfen nicht abzeichnen
        mvc.perform(post("/v1/berichtsheft/woche/" + wochenId + "/abzeichnen").header("Authorization", azubi))
                .andExpect(status().isForbidden());
    }

    @Test
    void lernzielCsvRoundTrip() throws Exception {
        String trainer = login("ausbilder", "ausbilder123");
        mvc.perform(multipart("/v1/lernziel/import")
                        .file(new org.springframework.mock.web.MockMultipartFile("file", "lernziele.csv", "text/csv",
                                ("titel;beschreibung;art;uebergeordnetes_lernziel\n"
                                        + "Datenbanken;SQL und Modellierung;Groblernziel;Software entwickeln\n"
                                        + "\"Joins; Subqueries\";;Feinlernziel;Datenbanken\n"
                                        + "Kaputt;;Unbekannt;\n").getBytes(java.nio.charset.StandardCharsets.UTF_8)))
                        .header("Authorization", trainer))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.importiert").value(2))
                .andExpect(jsonPath("$.fehler", hasSize(1)));

        mvc.perform(get("/v1/lernziel/export").header("Authorization", trainer))
                .andExpect(status().isOk())
                .andExpect(org.springframework.test.web.servlet.result.MockMvcResultMatchers.content()
                        .string(containsString("\"Joins; Subqueries\"")));
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
