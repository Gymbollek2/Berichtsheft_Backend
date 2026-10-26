package de.fisp.azubiprojekt.config;

import com.icegreen.greenmail.junit5.GreenMailExtension;
import com.icegreen.greenmail.util.ServerSetupTest;
import de.fisp.azubiprojekt.service.SmtpEmailVersand;
import de.fisp.azubiprojekt.service.VorbereiteteEmail;
import jakarta.mail.internet.MimeMessage;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;
import org.springframework.mail.javamail.JavaMailSenderImpl;
import org.springframework.mock.env.MockEnvironment;

import java.util.Properties;

import static org.assertj.core.api.Assertions.assertThat;

/** Aufbereitung der SMTP-Optionen aus spring.mail.* (leere Werte, EHLO-Name, mail.from). */
class EmailKonfigurationTest {

    private static final String ABSENDER = "absender@portal.test";

    @RegisterExtension
    static final GreenMailExtension MAILSERVER = new GreenMailExtension(ServerSetupTest.SMTP.dynamicPort());

    @Test
    void entferntLeereOptionenUndSetztEhloNameUndAbsender() {
        JavaMailSenderImpl sender = new JavaMailSenderImpl();
        sender.getJavaMailProperties().setProperty("mail.smtp.ssl.trust", "");
        sender.getJavaMailProperties().setProperty("mail.smtp.localhost", " ");

        aufbereiten(sender);

        Properties optionen = sender.getJavaMailProperties();
        assertThat(optionen).doesNotContainKey("mail.smtp.ssl.trust");
        assertThat(optionen.getProperty("mail.smtp.localhost")).isNotBlank();
        assertThat(optionen.getProperty("mail.from")).isEqualTo(ABSENDER);
    }

    @Test
    void gesetzterEhloNameBleibt() {
        JavaMailSenderImpl sender = new JavaMailSenderImpl();
        sender.getJavaMailProperties().setProperty("mail.smtp.localhost", "server.firma.test");

        aufbereiten(sender);

        assertThat(sender.getJavaMailProperties().getProperty("mail.smtp.localhost")).isEqualTo("server.firma.test");
    }

    /**
     * Ohne EHLO-Name und mail.from schlaegt Jakarta Mail den eigenen Rechnernamen rueckwaerts im DNS nach –
     * am Arbeitsplatz-PC dauerte so jede Mail rund 16 Sekunden.
     */
    @Test
    void versandWartetNichtAufDnsAbfragen() throws Exception {
        JavaMailSenderImpl sender = new JavaMailSenderImpl();
        sender.setHost("localhost");
        sender.setPort(MAILSERVER.getSmtp().getPort());
        aufbereiten(sender);
        SmtpEmailVersand versand = new SmtpEmailVersand(sender, eigenschaften());

        long start = System.nanoTime();
        versand.senden(new VorbereiteteEmail("max@firma.test", "Max", null, "Betreff", "Hallo", "<p>Hallo</p>"));
        long millis = (System.nanoTime() - start) / 1_000_000;

        MimeMessage[] angekommen = MAILSERVER.getReceivedMessages();
        assertThat(angekommen).hasSize(1);
        assertThat(angekommen[0].getMessageID()).endsWith("@portal.test>");
        assertThat(millis).as("Dauer des Versands in ms").isLessThan(5_000);
    }

    private static void aufbereiten(JavaMailSenderImpl sender) {
        MockEnvironment umgebung = new MockEnvironment().withProperty("app.mail.absender", ABSENDER);
        EmailKonfiguration.mailOptionenAufbereiten(umgebung).postProcessAfterInitialization(sender, "mailSender");
    }

    private static EmailEigenschaften eigenschaften() {
        return new EmailEigenschaften(true, EmailEigenschaften.Versandart.SMTP, ABSENDER, "Azubi Portal",
                "", "", "https://portal.test",
                new EmailEigenschaften.Empfaenger("{vorname}.{nachname}@firma.test", "{vorname}.{nachname}@firma.test",
                        false, "", "firma.test"),
                new EmailEigenschaften.Erinnerung(true, 7, 7, 8), null, null);
    }
}
