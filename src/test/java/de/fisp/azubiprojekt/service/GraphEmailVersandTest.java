package de.fisp.azubiprojekt.service;

import de.fisp.azubiprojekt.config.EmailEigenschaften;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.ExpectedCount;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.*;
import static org.springframework.test.web.client.response.MockRestResponseCreators.*;

/** Versand ueber Microsoft Graph gegen einen simulierten Microsoft-Server (keine echten Anfragen). */
class GraphEmailVersandTest {

    private static final String TOKEN_URL = "https://login.test/mandant-1/oauth2/v2.0/token";
    private static final String SEND_URL = "https://graph.test/v1.0/users/ausbildung%40firma.de/sendMail";
    private static final String TOKEN_ANTWORT = """
            {"token_type":"Bearer","expires_in":3599,"access_token":"token-1"}""";

    private MockRestServiceServer server;
    private GraphEmailVersand versand;

    @BeforeEach
    void setUp() {
        RestClient.Builder builder = RestClient.builder();
        server = MockRestServiceServer.bindTo(builder).build();
        versand = new GraphEmailVersand(eigenschaften("geheim", ""), builder);
    }

    @Test
    void holtTokenUndVerschicktMailAlsJson() {
        server.expect(requestTo(TOKEN_URL))
                .andExpect(method(HttpMethod.POST))
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_FORM_URLENCODED))
                .andExpect(content().formDataContains(java.util.Map.of(
                        "grant_type", "client_credentials", "client_id", "client-1", "client_secret", "geheim",
                        "scope", "https://graph.microsoft.com/.default")))
                .andRespond(withSuccess(TOKEN_ANTWORT, MediaType.APPLICATION_JSON));
        server.expect(ExpectedCount.twice(), requestTo(SEND_URL))
                .andExpect(method(HttpMethod.POST))
                .andExpect(header("Authorization", "Bearer token-1"))
                .andExpect(jsonPath("$.message.subject").value("Betreff"))
                .andExpect(jsonPath("$.message.body.contentType").value("HTML"))
                .andExpect(jsonPath("$.message.body.content").value("<p>Hallo</p>"))
                .andExpect(jsonPath("$.message.toRecipients[0].emailAddress.address").value("Max.Mustermann@firma.de"))
                .andExpect(jsonPath("$.message.toRecipients[0].emailAddress.name").value("Max Mustermann"))
                .andExpect(jsonPath("$.saveToSentItems").value(true))
                .andRespond(withStatus(HttpStatus.ACCEPTED));

        versand.senden(mail());
        // Zweite Mail nutzt das zwischengespeicherte Token (nur ein Token-Abruf erwartet)
        versand.senden(mail());
        server.verify();
    }

    @Test
    void holtNachAbgelehntemTokenEinNeues() {
        server.expect(requestTo(TOKEN_URL)).andRespond(withSuccess(TOKEN_ANTWORT, MediaType.APPLICATION_JSON));
        server.expect(requestTo(SEND_URL)).andRespond(withStatus(HttpStatus.UNAUTHORIZED));
        server.expect(requestTo(TOKEN_URL)).andRespond(withSuccess(TOKEN_ANTWORT.replace("token-1", "token-2"), MediaType.APPLICATION_JSON));
        server.expect(requestTo(SEND_URL)).andExpect(header("Authorization", "Bearer token-2"))
                .andRespond(withStatus(HttpStatus.ACCEPTED));

        versand.senden(mail());
        server.verify();
    }

    @Test
    void meldetFehlertextVonMicrosoft() {
        server.expect(requestTo(TOKEN_URL)).andRespond(withStatus(HttpStatus.UNAUTHORIZED)
                .contentType(MediaType.APPLICATION_JSON)
                .body("{\"error\":\"invalid_client\",\"error_description\":\"AADSTS7000215: Invalid client secret provided.\"}"));

        assertThatThrownBy(() -> versand.senden(mail()))
                .hasMessageContaining("Anmeldung bei Microsoft Entra ID fehlgeschlagen")
                .hasMessageContaining("AADSTS7000215");
    }

    @Test
    void meldetFehlendeRechteBeimSenden() {
        server.expect(requestTo(TOKEN_URL)).andRespond(withSuccess(TOKEN_ANTWORT, MediaType.APPLICATION_JSON));
        server.expect(requestTo(SEND_URL)).andRespond(withStatus(HttpStatus.FORBIDDEN)
                .contentType(MediaType.APPLICATION_JSON)
                .body("{\"error\":{\"code\":\"ErrorAccessDenied\",\"message\":\"Access is denied. Check credentials and try again.\"}}"));

        assertThatThrownBy(() -> versand.senden(mail()))
                .hasMessageContaining("HTTP 403")
                .hasMessageContaining("Access is denied");
    }

    @Test
    void umgeleiteteMailTraegtDenEigentlichenEmpfaenger() {
        var anfrage = versand.anfrage(mail().umgeleitetAn("test@firma.de"));
        assertThat(anfrage.toString()).contains("test@firma.de", "X-Original-To", "Max.Mustermann@firma.de");
    }

    @Test
    void ohneZugangsdatenStartetDerVersandNicht() {
        assertThatThrownBy(() -> new GraphEmailVersand(eigenschaften("", ""), RestClient.builder()))
                .hasMessageContaining("MAIL_GRAPH_CLIENT_SECRET");
    }

    @Test
    void postfachIstStandardmaessigDieAbsenderadresse() {
        assertThat(versand.postfach()).isEqualTo("ausbildung@firma.de");
        var mitPostfach = new GraphEmailVersand(eigenschaften("geheim", "anderes@firma.de"), RestClient.builder());
        assertThat(mitPostfach.postfach()).isEqualTo("anderes@firma.de");
    }

    private static VorbereiteteEmail mail() {
        return new VorbereiteteEmail("Max.Mustermann@firma.de", "Max Mustermann", null, "Betreff", "Hallo", "<p>Hallo</p>");
    }

    private static EmailEigenschaften eigenschaften(String secret, String postfach) {
        return new EmailEigenschaften(true, EmailEigenschaften.Versandart.GRAPH, "ausbildung@firma.de", "Azubi Portal",
                "", "", "https://portal.test",
                new EmailEigenschaften.Empfaenger("{vorname}.{nachname}@firma.de", "{vorname}.{nachname}@firma.de", false, "", "firma.de"),
                new EmailEigenschaften.Erinnerung(true, 7, 7, 8),
                new EmailEigenschaften.Graph("mandant-1", "client-1", secret, postfach, true,
                        "https://login.test/", "https://graph.test/v1.0", "https://graph.microsoft.com/.default", 5000),
                null);
    }
}
