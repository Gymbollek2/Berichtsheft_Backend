package de.fisp.azubiprojekt.integration;

import de.fisp.azubiprojekt.service.WindowsAnmeldung;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.util.Base64;
import java.util.Random;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

/**
 * Kerberos-Tickets von Benutzern mit vielen AD-Gruppen sind 10-40 KB gross. Sie muessen beim echten
 * Webserver (Tomcat) ankommen und duerfen nicht schon dort mit HTTP 400 abgewiesen werden.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT, properties = {
        "spring.datasource.url=jdbc:h2:mem:berichtsheft-header-test;DB_CLOSE_DELAY=-1",
        "spring.jpa.hibernate.ddl-auto=create-drop",
        "server.ssl.enabled=false",
        "app.mail.enabled=false",
        "app.demo-data.enabled=true",
        "app.rahmenplan-import.enabled=false",
        "app.sso.enabled=true"
})
class SsoHeaderGroesseTest {

    @LocalServerPort
    private int port;

    @MockitoBean
    private WindowsAnmeldung windowsAnmeldung;

    @Test
    void grossesKerberosTicketKommtBeiDerAnmeldungAn() throws Exception {
        when(windowsAnmeldung.pruefe(any(), any(), any())).thenReturn(new WindowsAnmeldung.Schritt(true, null, "FIRMA\\azubi"));
        byte[] ticket = new byte[30_000];
        new Random(1).nextBytes(ticket);

        HttpResponse<String> antwort = HttpClient.newHttpClient().send(
                HttpRequest.newBuilder(URI.create("http://localhost:" + port + "/v1/passport/sso"))
                        .header("Authorization", "Negotiate " + Base64.getEncoder().encodeToString(ticket))
                        .build(),
                HttpResponse.BodyHandlers.ofString());

        assertThat(antwort.statusCode()).isEqualTo(200);
        assertThat(antwort.body()).contains("accessToken");
    }
}
