package de.fisp.azubiprojekt.integration;

import com.jayway.jsonpath.JsonPath;
import de.fisp.azubiprojekt.service.WindowsAnmeldung;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.context.WebApplicationContext;

import java.util.Base64;

import static org.hamcrest.Matchers.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers.springSecurity;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/**
 * Windows-Anmeldung (SSO) ueber die REST-API. Windows selbst wird simuliert: Der Mock liefert das Konto,
 * das sonst Kerberos/NTLM ermittelt. Grundlage sind die Demo-Daten (ausbilder, azubi, azubi2).
 */
@SpringBootTest(properties = {
        "spring.datasource.url=jdbc:h2:mem:berichtsheft-sso-test;DB_CLOSE_DELAY=-1",
        "spring.jpa.hibernate.ddl-auto=create-drop",
        "server.ssl.enabled=false",
        "app.mail.enabled=false",
        "app.demo-data.enabled=true",
        "app.rahmenplan-import.enabled=false",
        "app.sso.enabled=true",
        "app.sso.domains=FIRMA",
        "app.password-login.enabled=false"
})
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_EACH_TEST_METHOD)
class SsoApiTest {

    private static final String TOKEN = Base64.getEncoder().encodeToString(new byte[]{1, 2, 3});

    @Autowired
    private WebApplicationContext context;

    @MockitoBean
    private WindowsAnmeldung windowsAnmeldung;

    private MockMvc mvc;

    @BeforeEach
    void setUp() {
        mvc = MockMvcBuilders.webAppContextSetup(context).apply(springSecurity()).build();
    }

    private void windowsKonto(String konto) {
        when(windowsAnmeldung.pruefe(any(), any(), any())).thenReturn(new WindowsAnmeldung.Schritt(true, null, konto));
    }

    @Test
    void optionenSindOhneAnmeldungAbrufbar() throws Exception {
        mvc.perform(get("/v1/passport/optionen"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.sso").value(true))
                .andExpect(jsonPath("$.passwortLogin").value(false));
    }

    @Test
    void ohneWindowsTokenFordertDerServerNegotiateAn() throws Exception {
        mvc.perform(get("/v1/passport/sso"))
                .andExpect(status().isUnauthorized())
                .andExpect(header().string("WWW-Authenticate", "Negotiate"));
    }

    @Test
    void gelisteterBenutzerWirdMitSeinerRolleAngemeldet() throws Exception {
        windowsKonto("FIRMA\\Azubi");
        String body = mvc.perform(get("/v1/passport/sso").header("Authorization", "Negotiate " + TOKEN))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.user.username").value("azubi"))
                .andExpect(jsonPath("$.user.role").value("ROLE_TRAINEE"))
                .andReturn().getResponse().getContentAsString();

        // Das Token funktioniert wie nach einem normalen Login
        mvc.perform(get("/v1/passport/me").header("Authorization", "Bearer " + JsonPath.read(body, "$.accessToken")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.username").value("azubi"));
    }

    @Test
    void nichtGelisteterBenutzerHatKeinenZugriff() throws Exception {
        windowsKonto("FIRMA\\fremder");
        mvc.perform(get("/v1/passport/sso").header("Authorization", "Negotiate " + TOKEN))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.message", containsString("nicht berechtigt")))
                .andExpect(jsonPath("$.konto").value("FIRMA\\fremder"))
                .andExpect(jsonPath("$.accessToken").doesNotExist());
    }

    @Test
    void kontoAusFremderDomaeneHatKeinenZugriff() throws Exception {
        windowsKonto("ANDERE\\azubi");
        mvc.perform(get("/v1/passport/sso").header("Authorization", "Negotiate " + TOKEN))
                .andExpect(status().isForbidden());
    }

    @Test
    void zwischenschrittWirdAnDenBrowserZurueckgegeben() throws Exception {
        when(windowsAnmeldung.pruefe(any(), any(), any())).thenReturn(new WindowsAnmeldung.Schritt(false, new byte[]{9}, null));
        mvc.perform(get("/v1/passport/sso").header("Authorization", "NTLM " + TOKEN))
                .andExpect(status().isUnauthorized())
                .andExpect(header().string("WWW-Authenticate", "NTLM " + Base64.getEncoder().encodeToString(new byte[]{9})));
    }

    @Test
    void fehlerhaftesTokenBrichtOhneErneuteAufforderungAb() throws Exception {
        when(windowsAnmeldung.pruefe(any(), any(), any())).thenThrow(new IllegalStateException("SEC_E_INVALID_TOKEN"));
        mvc.perform(get("/v1/passport/sso").header("Authorization", "Negotiate " + TOKEN))
                .andExpect(status().isUnauthorized())
                .andExpect(header().doesNotExist("WWW-Authenticate"));
        verify(windowsAnmeldung).abbrechen(any());
    }

    @Test
    void ohneTokenBleibtDieApiGesperrt() throws Exception {
        mvc.perform(get("/v1/berichtsheft/historie")).andExpect(status().isUnauthorized());
    }

    @Test
    void passwortLoginIstAbschaltbar() throws Exception {
        mvc.perform(post("/v1/passport/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"username\":\"azubi\",\"password\":\"azubi123\"}"))
                .andExpect(status().isForbidden());
    }
}
