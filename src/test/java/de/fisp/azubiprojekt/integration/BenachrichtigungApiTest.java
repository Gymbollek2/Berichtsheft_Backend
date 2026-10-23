package de.fisp.azubiprojekt.integration;

import com.jayway.jsonpath.JsonPath;
import de.fisp.azubiprojekt.service.BenachrichtigungService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.context.WebApplicationContext;

import java.time.DayOfWeek;
import java.time.LocalDate;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.*;
import static org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers.springSecurity;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/**
 * Sichtbarkeit nicht abgegebener Wochen fuer Ausbilder und Benachrichtigungen rund um das Berichtsheft.
 * Grundlage sind die Demo-Daten (ausbilder, azubi2 ohne Eintraege).
 */
@SpringBootTest(properties = {
        "spring.datasource.url=jdbc:h2:mem:benachrichtigung-test;DB_CLOSE_DELAY=-1",
        "spring.jpa.hibernate.ddl-auto=create-drop",
        "server.ssl.enabled=false",
        "app.demo-data.enabled=true",
        "app.rahmenplan-import.enabled=false",
        // Erinnerungen werden im Test gezielt ausgeloest
        "app.erinnerungen.enabled=false"
})
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_EACH_TEST_METHOD)
class BenachrichtigungApiTest {

    @Autowired
    private WebApplicationContext context;

    @Autowired
    private BenachrichtigungService benachrichtigungService;

    private MockMvc mvc;

    /** Montag einer abgeschlossenen Woche, die azubi2 noch nicht erfasst hat. */
    private final LocalDate montag = LocalDate.now().with(DayOfWeek.MONDAY).minusWeeks(2);

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

    private Integer personId(String token) throws Exception {
        return JsonPath.read(json(mvc.perform(get("/v1/passport/me").header("Authorization", token))), "$.id");
    }

    private void tagErfassen(String token, LocalDate date) throws Exception {
        mvc.perform(put("/v1/berichtsheft/eintrag").header("Authorization", token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"date\":\"" + date + "\",\"hours\":8,\"art\":\"BETRIEB\","
                                + "\"description\":\"Geheimer Entwurf\",\"lernzielIds\":[]}"))
                .andExpect(status().isOk());
    }

    private Integer wocheAbgeben(String token) throws Exception {
        for (int i = 0; i < 5; i++) {
            tagErfassen(token, montag.plusDays(i));
        }
        String woche = json(mvc.perform(post("/v1/berichtsheft/woche/einreichen").header("Authorization", token)
                        .contentType(MediaType.APPLICATION_JSON).content("{\"datum\":\"" + montag + "\"}"))
                .andExpect(status().isOk()));
        return JsonPath.read(woche, "$.id");
    }

    private String nachrichten(String token) throws Exception {
        return json(mvc.perform(get("/v1/benachrichtigung").header("Authorization", token)).andExpect(status().isOk()));
    }

    private long ungelesen(String token) throws Exception {
        Integer anzahl = JsonPath.read(json(mvc.perform(get("/v1/benachrichtigung/anzahl").header("Authorization", token))),
                "$.ungelesen");
        return anzahl;
    }

    // ------------------------------------------------------------------ Sichtbarkeit

    @Test
    void ausbilderSiehtNichtAbgegebeneWochenNicht() throws Exception {
        String azubi = login("azubi2", "azubi123");
        String trainer = login("ausbilder", "ausbilder123");
        Integer azubiId = personId(azubi);
        tagErfassen(azubi, montag);

        // Der Azubi selbst sieht seinen Entwurf
        mvc.perform(get("/v1/berichtsheft/woche").param("datum", montag.toString()).header("Authorization", azubi))
                .andExpect(jsonPath("$.eintraegeSichtbar").value(true))
                .andExpect(jsonPath("$.tage[0].eintrag.description").value("Geheimer Entwurf"));

        // Der Ausbilder nicht – weder in der Woche noch in der Uebersicht
        String woche = json(mvc.perform(get("/v1/berichtsheft/woche").param("personId", String.valueOf(azubiId))
                        .param("datum", montag.toString()).header("Authorization", trainer))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("ENTWURF"))
                .andExpect(jsonPath("$.eintraegeSichtbar").value(false))
                .andExpect(jsonPath("$.gesamtStunden").value(0.0)));
        assertThat(woche).doesNotContain("Geheimer Entwurf");
        mvc.perform(get("/v1/berichtsheft/historie").param("personId", String.valueOf(azubiId)).header("Authorization", trainer))
                .andExpect(jsonPath("$[*].weekStart", not(hasItem(montag.toString()))));

        // Nach dem Abgeben ist alles sichtbar
        wocheAbgeben(azubi);
        mvc.perform(get("/v1/berichtsheft/woche").param("personId", String.valueOf(azubiId))
                        .param("datum", montag.toString()).header("Authorization", trainer))
                .andExpect(jsonPath("$.eintraegeSichtbar").value(true))
                .andExpect(jsonPath("$.gesamtStunden").value(40.0))
                .andExpect(jsonPath("$.tage[0].eintrag.description").value("Geheimer Entwurf"));
        mvc.perform(get("/v1/berichtsheft/historie").param("personId", String.valueOf(azubiId)).header("Authorization", trainer))
                .andExpect(jsonPath("$[*].weekStart", hasItem(montag.toString())));
    }

    // ------------------------------------------------------------------ Benachrichtigungen

    @Test
    void benachrichtigungenRundUmDasBerichtsheft() throws Exception {
        String azubi = login("azubi2", "azubi123");
        String trainer = login("ausbilder", "ausbilder123");
        Integer azubiId = personId(azubi);
        long vorher = ungelesen(trainer);

        // 1. Abgabe -> Ausbilder wird benachrichtigt, mit Link auf die Woche
        Integer wochenId = wocheAbgeben(azubi);
        assertThat(ungelesen(trainer)).isEqualTo(vorher + 1);
        mvc.perform(get("/v1/benachrichtigung").header("Authorization", trainer))
                .andExpect(jsonPath("$.eintraege[0].typ").value("BERICHT_ABGEGEBEN"))
                .andExpect(jsonPath("$.eintraege[0].titel").value("Berichtsheft abgegeben"))
                .andExpect(jsonPath("$.eintraege[0].text", containsString("Erika Musterfrau")))
                .andExpect(jsonPath("$.eintraege[0].link").value("/berichtsheft/azubis/" + azubiId + "?datum=" + montag))
                .andExpect(jsonPath("$.eintraege[0].gelesen").value(false));

        // 2. Zurueckgeben -> Azubi bekommt "Korrekturbedarf" mit Kommentar, beim Ausbilder ist die Abgabe erledigt
        mvc.perform(post("/v1/berichtsheft/woche/" + wochenId + "/ablehnen").header("Authorization", trainer)
                        .contentType(MediaType.APPLICATION_JSON).content("{\"kommentar\":\"Bitte Dienstag genauer\"}"))
                .andExpect(status().isOk());
        assertThat(ungelesen(trainer)).isEqualTo(vorher);
        mvc.perform(get("/v1/benachrichtigung").header("Authorization", azubi))
                .andExpect(jsonPath("$.eintraege[0].typ").value("BERICHT_ZURUECKGEGEBEN"))
                .andExpect(jsonPath("$.eintraege[0].titel").value("Korrekturbedarf"))
                .andExpect(jsonPath("$.eintraege[0].text", containsString("Bitte Dienstag genauer")))
                .andExpect(jsonPath("$.eintraege[0].link").value("/berichtsheft?datum=" + montag));

        // 3. Erneut abgeben -> "erneut abgegeben"
        mvc.perform(post("/v1/berichtsheft/woche/einreichen").header("Authorization", azubi)
                        .contentType(MediaType.APPLICATION_JSON).content("{\"datum\":\"" + montag + "\"}"))
                .andExpect(status().isOk());
        mvc.perform(get("/v1/benachrichtigung").header("Authorization", trainer))
                .andExpect(jsonPath("$.eintraege[0].titel").value("Berichtsheft erneut abgegeben"));

        // 4. Abzeichnen -> Azubi bekommt "Berichtsheft abgenommen"
        mvc.perform(post("/v1/berichtsheft/woche/" + wochenId + "/abzeichnen").header("Authorization", trainer)
                        .contentType(MediaType.APPLICATION_JSON).content("{\"kommentar\":\"Sehr gut\"}"))
                .andExpect(status().isOk());
        mvc.perform(get("/v1/benachrichtigung").header("Authorization", azubi))
                .andExpect(jsonPath("$.ungelesen").value(2))
                .andExpect(jsonPath("$.eintraege[0].typ").value("BERICHT_ABGEZEICHNET"))
                .andExpect(jsonPath("$.eintraege[0].titel").value("Berichtsheft abgenommen"))
                .andExpect(jsonPath("$.eintraege[0].text", containsString("Sehr gut")));
        assertThat(ungelesen(trainer)).isEqualTo(vorher);
    }

    @Test
    void abgenommeneWocheWiederOeffnen() throws Exception {
        String azubi = login("azubi2", "azubi123");
        String trainer = login("ausbilder", "ausbilder123");
        Integer wochenId = wocheAbgeben(azubi);
        String wiederOeffnen = "/v1/berichtsheft/woche/" + wochenId + "/wieder-oeffnen";

        // Nur abgenommene Wochen, nur Ausbilder
        mvc.perform(post(wiederOeffnen).header("Authorization", trainer)).andExpect(status().isBadRequest());
        mvc.perform(post("/v1/berichtsheft/woche/" + wochenId + "/abzeichnen").header("Authorization", trainer)
                        .contentType(MediaType.APPLICATION_JSON).content("{\"kommentar\":\"Passt\"}"))
                .andExpect(status().isOk());
        mvc.perform(post(wiederOeffnen).header("Authorization", azubi)).andExpect(status().isForbidden());

        mvc.perform(post(wiederOeffnen).header("Authorization", trainer)
                        .contentType(MediaType.APPLICATION_JSON).content("{\"kommentar\":\"Bitte Freitag ergänzen\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("ABGELEHNT"))
                .andExpect(jsonPath("$.kommentar").value("Bitte Freitag ergänzen"));

        // Der Azubi wird benachrichtigt und kann wieder bearbeiten und erneut abgeben
        mvc.perform(get("/v1/benachrichtigung").header("Authorization", azubi))
                .andExpect(jsonPath("$.eintraege[0].typ").value("BERICHT_WIEDER_GEOEFFNET"))
                .andExpect(jsonPath("$.eintraege[0].titel").value("Berichtsheft wieder geöffnet"))
                .andExpect(jsonPath("$.eintraege[0].text", containsString("Bitte Freitag ergänzen")));
        mvc.perform(get("/v1/berichtsheft/woche").param("datum", montag.toString()).header("Authorization", azubi))
                .andExpect(jsonPath("$.editierbar").value(true));
        tagErfassen(azubi, montag.plusDays(4));
        mvc.perform(post("/v1/berichtsheft/woche/einreichen").header("Authorization", azubi)
                        .contentType(MediaType.APPLICATION_JSON).content("{\"datum\":\"" + montag + "\"}"))
                .andExpect(jsonPath("$.status").value("EINGEREICHT"));

        // Ohne Grund geht es auch (nach erneuter Abnahme)
        mvc.perform(post("/v1/berichtsheft/woche/" + wochenId + "/abzeichnen").header("Authorization", trainer))
                .andExpect(status().isOk());
        mvc.perform(post(wiederOeffnen).header("Authorization", trainer))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.kommentar").value(nullValue()));
    }

    @Test
    void gelesenMarkierenNurFuerEigeneNachrichten() throws Exception {
        String azubi = login("azubi2", "azubi123");
        String trainer = login("ausbilder", "ausbilder123");
        Integer wochenId = wocheAbgeben(azubi);
        mvc.perform(post("/v1/berichtsheft/woche/" + wochenId + "/abzeichnen").header("Authorization", trainer))
                .andExpect(status().isOk());

        Integer nachricht = JsonPath.read(nachrichten(azubi), "$.eintraege[0].id");
        // Fremde Nachrichten sind nicht erreichbar
        mvc.perform(post("/v1/benachrichtigung/" + nachricht + "/gelesen").header("Authorization", trainer))
                .andExpect(status().isNotFound());

        mvc.perform(post("/v1/benachrichtigung/" + nachricht + "/gelesen").header("Authorization", azubi))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.gelesen").value(true));
        assertThat(ungelesen(azubi)).isZero();

        // "Alle gelesen"
        mvc.perform(post("/v1/benachrichtigung/gelesen").header("Authorization", trainer)).andExpect(status().isOk());
        assertThat(ungelesen(trainer)).isZero();
    }

    @Test
    void liveVerbindungMeldetNeueNachrichtenSofort() throws Exception {
        String azubi = login("azubi2", "azubi123");
        String trainer = login("ausbilder", "ausbilder123");
        mvc.perform(get("/v1/benachrichtigung/live")).andExpect(status().isUnauthorized());

        MvcResult ausbilderLive = mvc.perform(get("/v1/benachrichtigung/live").header("Authorization", trainer))
                .andExpect(request().asyncStarted())
                .andReturn();
        // Ein anderer Azubi, den die Abgabe nichts angeht
        MvcResult andererLive = mvc.perform(get("/v1/benachrichtigung/live")
                        .header("Authorization", login("azubi", "azubi123")))
                .andExpect(request().asyncStarted())
                .andReturn();
        assertThat(ausbilderLive.getResponse().getContentType()).startsWith(MediaType.TEXT_EVENT_STREAM_VALUE);
        assertThat(ausbilderLive.getResponse().getContentAsString()).contains("event:verbunden");

        // Abgabe -> der Ausbilder bekommt sofort ein Ereignis, Unbeteiligte nicht
        Integer wochenId = wocheAbgeben(azubi);
        assertThat(ausbilderLive.getResponse().getContentAsString()).contains("event:benachrichtigung");
        assertThat(andererLive.getResponse().getContentAsString()).doesNotContain("event:benachrichtigung");

        // Abzeichnen -> der Azubi bekommt sofort ein Ereignis
        MvcResult azubiLive = mvc.perform(get("/v1/benachrichtigung/live").header("Authorization", azubi))
                .andExpect(request().asyncStarted())
                .andReturn();
        mvc.perform(post("/v1/berichtsheft/woche/" + wochenId + "/abzeichnen").header("Authorization", trainer))
                .andExpect(status().isOk());
        assertThat(azubiLive.getResponse().getContentAsString()).contains("event:benachrichtigung");
    }

    @Test
    void erinnerungAnNichtAbgegebeneVorwoche() throws Exception {
        String azubi = login("azubi2", "azubi123");
        LocalDate heute = montag.plusWeeks(1).plusDays(2);

        assertThat(benachrichtigungService.erinnerungenErzeugen(heute)).isPositive();
        mvc.perform(get("/v1/benachrichtigung").header("Authorization", azubi))
                .andExpect(jsonPath("$.ungelesen").value(1))
                .andExpect(jsonPath("$.eintraege[0].typ").value("ERINNERUNG_ABGABE"))
                .andExpect(jsonPath("$.eintraege[0].link").value("/berichtsheft?datum=" + montag));

        // Je Woche nur eine Erinnerung
        benachrichtigungService.erinnerungenErzeugen(heute);
        assertThat(ungelesen(azubi)).isEqualTo(1);

        // Nach dem Abgeben ist die Erinnerung erledigt
        wocheAbgeben(azubi);
        assertThat(ungelesen(azubi)).isZero();
    }

    @Test
    void lernzielErreichtUndWillkommen() throws Exception {
        String azubi = login("azubi2", "azubi123");
        String trainer = login("ausbilder", "ausbilder123");
        Integer azubiId = personId(azubi);

        String lernziele = json(mvc.perform(get("/v1/personLernziel/person/" + azubiId).header("Authorization", trainer)));
        Integer zuordnung = JsonPath.<List<Integer>>read(lernziele, "$[?(@.lernziel.kind == 'KIND_SPECIFIC')].id").get(0);
        mvc.perform(put("/v1/personLernziel/" + zuordnung + "/status").header("Authorization", trainer)
                        .contentType(MediaType.APPLICATION_JSON).content("{\"status\":\"STATUS_DONE\"}"))
                .andExpect(status().isOk());
        mvc.perform(get("/v1/benachrichtigung").header("Authorization", azubi))
                .andExpect(jsonPath("$.eintraege[0].typ").value("LERNZIEL_ERREICHT"))
                .andExpect(jsonPath("$.eintraege[0].text", containsString("Petra Pruefer")));

        // Neue Person bekommt eine Willkommensnachricht
        mvc.perform(post("/v1/person").header("Authorization", trainer)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"firstName\":\"Neu\",\"lastName\":\"Ling\",\"role\":\"ROLE_TRAINER\","
                                + "\"password\":\"geheim123\"}"))
                .andExpect(status().isCreated());
        String neu = login("neu.ling", "geheim123");
        mvc.perform(get("/v1/benachrichtigung").header("Authorization", neu))
                .andExpect(jsonPath("$.eintraege[0].typ").value("WILLKOMMEN"))
                .andExpect(jsonPath("$.eintraege[0].link").value("/profil"));

        // Personen mit Nachrichten lassen sich loeschen
        Integer neuId = personId(neu);
        mvc.perform(delete("/v1/person/" + neuId).header("Authorization", trainer)).andExpect(status().isOk());
    }
}
