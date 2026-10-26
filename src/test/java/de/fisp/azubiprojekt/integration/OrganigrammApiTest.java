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

import java.time.LocalDate;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers.springSecurity;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Ausbildungsorganigramm ueber die REST-API. Demo-Daten: Petra Pruefer (Ausbilderin), Max Mustermann (2. Jahr),
 * Erika Musterfrau (1. Jahr).
 */
@SpringBootTest(properties = {
        "spring.datasource.url=jdbc:h2:mem:organigramm-test;DB_CLOSE_DELAY=-1",
        "spring.jpa.hibernate.ddl-auto=create-drop",
        "server.ssl.enabled=false",
        "app.mail.enabled=false",
        "app.demo-data.enabled=true",
        "app.rahmenplan-import.enabled=false"
})
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_EACH_TEST_METHOD)
class OrganigrammApiTest {

    @Autowired
    private WebApplicationContext context;

    private MockMvc mvc;

    @BeforeEach
    void setUp() {
        mvc = MockMvcBuilders.webAppContextSetup(context).apply(springSecurity()).build();
    }

    @Test
    void azubisSehenAusbilderObenUndJahrgaengeAbsteigend() throws Exception {
        String body = organigramm(login("azubi", "azubi123"));

        assertThat(JsonPath.<List<String>>read(body, "$.ausbilder[*].fullName")).containsExactly("Petra Pruefer");
        assertThat(JsonPath.<List<Integer>>read(body, "$.jahrgaenge[*].ausbildungsjahr")).containsExactly(2, 1);
        assertThat(JsonPath.<List<String>>read(body, "$.jahrgaenge[0].mitglieder[*].fullName")).containsExactly("Max Mustermann");
        assertThat(JsonPath.<List<String>>read(body, "$.jahrgaenge[1].mitglieder[*].fullName")).containsExactly("Erika Musterfrau");
        // Nur das Noetigste – keine Benutzernamen oder Geburtsdaten
        assertThat(body).doesNotContain("username", "dateOfBirth", "azubi2");
    }

    @Test
    void zukuenftigeAzubisErscheinenErstAbAusbildungsbeginn() throws Exception {
        String ausbilder = login("ausbilder", "ausbilder123");
        anlegen(ausbilder, "Nina", "Neu", LocalDate.now().plusMonths(1));
        anlegen(ausbilder, "Hans", "Heute", LocalDate.now());
        anlegen(ausbilder, "Dora", "Dritte", LocalDate.now().minusYears(2).minusDays(1));

        String body = organigramm(ausbilder);

        assertThat(body).doesNotContain("Nina");
        assertThat(JsonPath.<List<Integer>>read(body, "$.jahrgaenge[*].ausbildungsjahr")).containsExactly(3, 2, 1);
        assertThat(JsonPath.<List<String>>read(body, "$.jahrgaenge[0].mitglieder[*].fullName")).containsExactly("Dora Dritte");
        assertThat(JsonPath.<List<String>>read(body, "$.jahrgaenge[2].mitglieder[*].fullName"))
                .containsExactly("Hans Heute", "Erika Musterfrau");
    }

    @Test
    void ohneAnmeldungGibtEsKeinOrganigramm() throws Exception {
        mvc.perform(get("/v1/person/organigramm")).andExpect(status().isUnauthorized());
    }

    // ------------------------------------------------------------------ Hilfsmethoden

    private String organigramm(String token) throws Exception {
        return mvc.perform(get("/v1/person/organigramm").header("Authorization", token))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
    }

    private void anlegen(String token, String vorname, String nachname, LocalDate beginn) throws Exception {
        mvc.perform(post("/v1/person").header("Authorization", token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"firstName\":\"" + vorname + "\",\"lastName\":\"" + nachname + "\",\"role\":\"ROLE_TRAINEE\","
                                + "\"password\":\"Start12345\",\"dateOfTrainingStart\":\"" + beginn + "\"}"))
                .andExpect(status().isCreated());
    }

    private String login(String username, String password) throws Exception {
        String body = mvc.perform(post("/v1/passport/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"username\":\"" + username + "\",\"password\":\"" + password + "\"}"))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        return "Bearer " + JsonPath.read(body, "$.accessToken");
    }
}
