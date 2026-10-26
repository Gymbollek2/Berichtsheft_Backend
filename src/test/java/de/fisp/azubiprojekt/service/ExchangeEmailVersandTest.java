package de.fisp.azubiprojekt.service;

import com.icegreen.greenmail.imap.ImapHostManager;
import com.icegreen.greenmail.junit5.GreenMailExtension;
import com.icegreen.greenmail.store.MailFolder;
import com.icegreen.greenmail.store.StoredMessage;
import com.icegreen.greenmail.user.GreenMailUser;
import com.icegreen.greenmail.util.ServerSetup;
import com.icegreen.greenmail.util.ServerSetupTest;
import de.fisp.azubiprojekt.config.EmailEigenschaften;
import jakarta.mail.Flags;
import jakarta.mail.internet.InternetAddress;
import jakarta.mail.internet.MimeMessage;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Versandart "exchange" gegen einen echten Mailserver im Test (GreenMail): SMTP ueber SSL mit Anmeldung,
 * danach die Kopie per IMAP ueber SSL in "Gesendete Elemente".
 */
class ExchangeEmailVersandTest {

    private static final String POSTFACH = "ausbildung@firma.test";
    private static final String PASSWORT = "geheim";
    private static final String GESENDET = "Gesendete Elemente";

    @RegisterExtension
    static final GreenMailExtension MAILSERVER = new GreenMailExtension(
            new ServerSetup[]{ServerSetupTest.SMTPS.dynamicPort(), ServerSetupTest.IMAPS.dynamicPort()});

    private GreenMailUser postfach;

    @BeforeEach
    void setUp() throws Exception {
        postfach = MAILSERVER.setUser(POSTFACH, POSTFACH, PASSWORT);
        imap().createMailbox(postfach, GESENDET);
    }

    @Test
    void verschicktPerSmtpUndLegtGeleseneKopieInGesendetAb() throws Exception {
        new ExchangeEmailVersand(eigenschaften(GESENDET, PASSWORT)).senden(mail());

        MimeMessage[] angekommen = beimEmpfaenger();
        assertThat(angekommen).hasSize(1);
        assertThat(((InternetAddress) angekommen[0].getAllRecipients()[0]).getAddress()).isEqualTo("max.mustermann@firma.test");
        assertThat(((InternetAddress) angekommen[0].getFrom()[0]).getAddress()).isEqualTo(POSTFACH);
        assertThat(angekommen[0].getMessageID()).endsWith("@firma.test>");

        MailFolder gesendet = imap().getFolder(postfach, GESENDET);
        assertThat(gesendet.getMessageCount()).isEqualTo(1);
        assertThat(gesendet.getMessages().getFirst().getMimeMessage().getSubject()).isEqualTo("Betreff");
        assertThat(gesendet.getMessages().getFirst().isSet(Flags.Flag.SEEN)).isTrue();
    }

    @Test
    void fehlenderOrdnerVerhindertDenVersandNicht() throws Exception {
        new ExchangeEmailVersand(eigenschaften("Sent Items", PASSWORT)).senden(mail());

        assertThat(beimEmpfaenger()).hasSize(1);
        assertThat(imap().getFolder(postfach, GESENDET).getMessageCount()).isZero();
    }

    @Test
    void falschesPasswortIstEinVersandfehler() throws Exception {
        ExchangeEmailVersand versand = new ExchangeEmailVersand(eigenschaften(GESENDET, "falsch"));

        assertThatThrownBy(() -> versand.senden(mail())).isInstanceOf(Exception.class);
        assertThat(beimEmpfaenger()).isEmpty();
    }

    @Test
    void fehlendeEinstellungenVerhindernDenStart() {
        EmailEigenschaften ohne = eigenschaften(new EmailEigenschaften.Exchange("", 587, true, false, "", "", "",
                true, 5000, "", true, "", 993, true, GESENDET));

        assertThatThrownBy(() -> new ExchangeEmailVersand(ohne))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("app.mail.exchange.smtp-host")
                .hasMessageContaining("app.mail.exchange.benutzername")
                .hasMessageContaining("app.mail.exchange.passwort");
    }

    @Test
    void imapServerIstStandardmaessigDerSmtpServer() {
        EmailEigenschaften e = eigenschaften(new EmailEigenschaften.Exchange(" mail.firma.test ", 587, true, false,
                POSTFACH, PASSWORT, "", true, 5000, "", true, "", 993, true, GESENDET));

        assertThat(new ExchangeEmailVersand(e).imapHost()).isEqualTo("mail.firma.test");
    }

    // ------------------------------------------------------------------ Hilfsmethoden

    /** Posteingang des Empfaengers – getReceivedMessages() zaehlt auch die Kopie in "Gesendete Elemente" mit. */
    private static MimeMessage[] beimEmpfaenger() throws Exception {
        GreenMailUser empfaenger = MAILSERVER.getUserManager().getUser("max.mustermann@firma.test");
        if (empfaenger == null) {
            return new MimeMessage[0];
        }
        return imap().getInbox(empfaenger).getMessages().stream().map(StoredMessage::getMimeMessage).toArray(MimeMessage[]::new);
    }

    private static ImapHostManager imap() {
        return MAILSERVER.getManagers().getImapHostManager();
    }

    private static VorbereiteteEmail mail() {
        return new VorbereiteteEmail("max.mustermann@firma.test", "Max Mustermann", null, "Betreff", "Hallo", "<p>Hallo</p>");
    }

    /** Verbindet sich ueber SSL mit GreenMail (selbstsigniertes Zertifikat ohne Servernamen, daher ssl-trust). */
    private static EmailEigenschaften eigenschaften(String gesendetOrdner, String passwort) {
        return eigenschaften(new EmailEigenschaften.Exchange("localhost", MAILSERVER.getSmtps().getPort(), false, true,
                POSTFACH, passwort, "*", false, 5000, "", true, "localhost", MAILSERVER.getImaps().getPort(), true, gesendetOrdner));
    }

    private static EmailEigenschaften eigenschaften(EmailEigenschaften.Exchange exchange) {
        return new EmailEigenschaften(true, EmailEigenschaften.Versandart.EXCHANGE, POSTFACH, "Azubi Portal",
                "", "", "https://portal.test",
                new EmailEigenschaften.Empfaenger("{vorname}.{nachname}@firma.test", "{vorname}.{nachname}@firma.test",
                        false, "", "firma.test"),
                new EmailEigenschaften.Erinnerung(true, 7, 7, 8), null, exchange);
    }
}
