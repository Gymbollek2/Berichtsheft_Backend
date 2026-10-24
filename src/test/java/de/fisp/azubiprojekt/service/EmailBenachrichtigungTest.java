package de.fisp.azubiprojekt.service;

import com.jayway.jsonpath.JsonPath;
import jakarta.mail.BodyPart;
import jakarta.mail.Multipart;
import jakarta.mail.Part;
import jakarta.mail.Session;
import jakarta.mail.internet.InternetAddress;
import jakarta.mail.internet.MimeMessage;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.web.context.WebApplicationContext;

import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.*;
import static org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers.springSecurity;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * E-Mails bei Abnahme/Rueckgabe und die Erinnerungen. Der Mailserver wird durch einen Mock ersetzt.
 * Grundlage sind die Demo-Daten: Max Mustermann hat die letzte Woche vor 2 Tagen eingereicht, Erika Musterfrau
 * (Beginn vor 3 Monaten) hat noch nichts abgegeben, Ausbilderin ist Petra Pruefer.
 */
@SpringBootTest(properties = {
        "spring.datasource.url=jdbc:h2:mem:email-test;DB_CLOSE_DELAY=-1",
        "spring.jpa.hibernate.ddl-auto=create-drop",
        "server.ssl.enabled=false",
        "app.demo-data.enabled=true",
        "app.rahmenplan-import.enabled=false",
        "app.mail.enabled=true",
        // Erinnerungen werden im Test gezielt aufgerufen, nicht beim Start
        "app.mail.erinnerung.enabled=false",
        "app.mail.portal-url=https://portal.test/",
        "app.mail.absender=absender@portal.test",
        "app.mail.versandart=smtp",
        "app.mail.empfaenger.umleiten-an="
})
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_EACH_TEST_METHOD)
class EmailBenachrichtigungTest {

    @MockitoBean
    private JavaMailSender mailSender;

    @Autowired
    private WebApplicationContext context;

    @Autowired
    private EmailBenachrichtigungService emailBenachrichtigungService;

    @Autowired
    private PlatformTransactionManager transactionManager;

    private MockMvc mvc;

    @BeforeEach
    void setUp() {
        mvc = MockMvcBuilders.webAppContextSetup(context).apply(springSecurity()).build();
        when(mailSender.createMimeMessage()).thenAnswer(i -> new MimeMessage((Session) null));
    }

    @Test
    void abnahmeSchicktMailAnAzubi() throws Exception {
        pruefen("abzeichnen", "{\"kommentar\":\"Sehr sauber dokumentiert\"}");

        MimeMessage mail = gesendet(1).getFirst();
        assertThat(((InternetAddress) mail.getFrom()[0]).getAddress()).isEqualTo("absender@portal.test");
        assertThat(((InternetAddress) mail.getAllRecipients()[0]).getAddress()).isEqualTo("Max.Mustermann@Azubi.de");
        assertThat(mail.getSubject()).startsWith("[Azubi Portal] Dein Berichtsheft für KW").endsWith("wurde abgenommen");
        String html = inhalt(mail, "text/html");
        assertThat(html).contains("Petra Pruefer", "Sehr sauber dokumentiert",
                "href=\"https://portal.test/berichtsheft?datum=" + letzteWoche() + "\"");
        assertThat(inhalt(mail, "text/plain")).contains("Woche ansehen: https://portal.test/berichtsheft?datum=" + letzteWoche());
    }

    @Test
    void rueckgabeSchicktMailMitKommentarAnAzubi() throws Exception {
        pruefen("ablehnen", "{\"kommentar\":\"Bitte Mittwoch <genauer> beschreiben\"}");

        MimeMessage mail = gesendet(1).getFirst();
        assertThat(mail.getSubject()).contains("braucht eine Überarbeitung");
        // Kommentar wird escapt
        assertThat(inhalt(mail, "text/html")).contains("Bitte Mittwoch &lt;genauer&gt; beschreiben", "Berichtsheft überarbeiten");
    }

    @Test
    void ausbilderWerdenNachEinerWocheOhneAbnahmeErinnert() throws Exception {
        // Eingereicht vor 2 Tagen: noch keine Erinnerung
        assertThat(imTx(() -> emailBenachrichtigungService.erinnerungenAnAusbilder(LocalDateTime.now()))).isZero();

        // 6 Tage spaeter ist die Abgabe 8 Tage her
        LocalDateTime spaeter = LocalDateTime.now().plusDays(6);
        assertThat(imTx(() -> emailBenachrichtigungService.erinnerungenAnAusbilder(spaeter))).isEqualTo(1);
        MimeMessage mail = gesendet(1).getFirst();
        assertThat(((InternetAddress) mail.getAllRecipients()[0]).getAddress()).isEqualTo("Petra.Pruefer@Azubi.de");
        assertThat(mail.getSubject()).contains("Max Mustermann", "wartet seit 8 Tagen");
        assertThat(inhalt(mail, "text/html")).contains("https://portal.test/berichtsheft/azubis/");

        // Jede Woche nur einmal
        assertThat(imTx(() -> emailBenachrichtigungService.erinnerungenAnAusbilder(spaeter.plusDays(1)))).isZero();
    }

    @Test
    void azubisWerdenAnUeberfaelligeWochenErinnert() throws Exception {
        LocalDate heute = LocalDate.now();
        // Max (letzte Woche eingereicht, aeltere Wochen offen) und Erika (nichts abgegeben)
        assertThat(imTx(() -> emailBenachrichtigungService.erinnerungenAnAzubis(heute))).isEqualTo(2);
        List<MimeMessage> mails = gesendet(2);
        assertThat(mails).extracting(m -> ((InternetAddress) m.getAllRecipients()[0]).getAddress())
                .containsExactlyInAnyOrder("Max.Mustermann@Azubi.de", "Erika.Musterfrau@Azubi.de");
        // Eingereichte und abgenommene Wochen werden nicht angemahnt
        MimeMessage max = mails.stream()
                .filter(m -> adresse(m).startsWith("Max")).findFirst().orElseThrow();
        String text = inhalt(max, "text/plain");
        assertThat(text).doesNotContain("KW " + BerichtsheftService.kalenderwoche(letzteWoche()) + "/")
                .doesNotContain("KW " + BerichtsheftService.kalenderwoche(letzteWoche().minusWeeks(1)) + "/");

        // Keine doppelten Erinnerungen
        assertThat(imTx(() -> emailBenachrichtigungService.erinnerungenAnAzubis(heute))).isZero();
    }

    @Test
    void adresseWirdAusDemNamenGebildet() {
        assertThat(EmailService.adressteil("Jürgen")).isEqualTo("Juergen");
        assertThat(EmailService.adressteil(" Weiß ")).isEqualTo("Weiss");
        assertThat(EmailService.adressteil("Anna Lena")).isEqualTo("Anna-Lena");
        assertThat(EmailService.adressteil("José")).isEqualTo("Jose");
    }

    // ------------------------------------------------------------------ Hilfsmethoden

    private void pruefen(String aktion, String body) throws Exception {
        String token = login("ausbilder", "ausbilder123");
        String offen = mvc.perform(get("/v1/berichtsheft/offen").header("Authorization", token))
                .andReturn().getResponse().getContentAsString();
        Integer id = JsonPath.read(offen, "$[0].id");
        mvc.perform(post("/v1/berichtsheft/woche/" + id + "/" + aktion).header("Authorization", token)
                        .contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isOk());
    }

    private String login(String username, String password) throws Exception {
        String body = mvc.perform(post("/v1/passport/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"username\":\"" + username + "\",\"password\":\"" + password + "\"}"))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        return "Bearer " + JsonPath.read(body, "$.accessToken");
    }

    /** Wartet auf den (evtl. asynchronen) Versand und liefert die Mails. */
    private List<MimeMessage> gesendet(int anzahl) {
        ArgumentCaptor<MimeMessage> captor = ArgumentCaptor.forClass(MimeMessage.class);
        verify(mailSender, timeout(5000).times(anzahl)).send(captor.capture());
        captor.getAllValues().forEach(m -> {
            try {
                // Setzt die Content-Type-Header der Teile, wie es beim echten Versand passiert
                m.saveChanges();
            } catch (Exception e) {
                throw new IllegalStateException(e);
            }
        });
        return captor.getAllValues();
    }

    private int imTx(java.util.function.IntSupplier aktion) {
        Integer ergebnis = new TransactionTemplate(transactionManager).execute(s -> aktion.getAsInt());
        return ergebnis == null ? 0 : ergebnis;
    }

    private static String adresse(MimeMessage m) {
        try {
            return ((InternetAddress) m.getAllRecipients()[0]).getAddress();
        } catch (Exception e) {
            throw new IllegalStateException(e);
        }
    }

    private static LocalDate letzteWoche() {
        return LocalDate.now().with(DayOfWeek.MONDAY).minusWeeks(1);
    }

    /** Sucht den Teil mit dem angegebenen Typ (text/html oder text/plain) in der Mail. */
    private static String inhalt(Part teil, String typ) throws Exception {
        if (teil.isMimeType(typ)) {
            return (String) teil.getContent();
        }
        if (teil.getContent() instanceof Multipart multipart) {
            for (int i = 0; i < multipart.getCount(); i++) {
                BodyPart unter = multipart.getBodyPart(i);
                String gefunden = inhalt(unter, typ);
                if (gefunden != null) {
                    return gefunden;
                }
            }
        }
        return null;
    }
}
