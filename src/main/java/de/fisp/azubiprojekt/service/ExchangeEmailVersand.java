package de.fisp.azubiprojekt.service;

import de.fisp.azubiprojekt.config.EmailEigenschaften;
import jakarta.mail.Flags;
import jakarta.mail.Folder;
import jakarta.mail.Message;
import jakarta.mail.MessagingException;
import jakarta.mail.Session;
import jakarta.mail.Store;
import jakarta.mail.internet.MimeMessage;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.mail.javamail.JavaMailSenderImpl;

import java.io.UnsupportedEncodingException;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Properties;

/**
 * Versand ueber einen eigenen Exchange-Server ({@code app.mail.exchange.*}): Die Mail geht per SMTP mit Anmeldung
 * raus (Client-Submission, meist Port 587 mit STARTTLS). Danach wird per IMAP eine Kopie in den Ordner
 * "Gesendete Elemente" des Postfachs gelegt – bei SMTP-Versand macht Exchange das nicht selbst.
 * Scheitert nur die Kopie, gilt die Mail trotzdem als verschickt (Warnung im Log).
 */
public class ExchangeEmailVersand extends SmtpEmailVersand {

    private static final Logger log = LoggerFactory.getLogger(ExchangeEmailVersand.class);

    private final EmailEigenschaften.Exchange exchange;
    private final JavaMailSenderImpl smtp;

    public ExchangeEmailVersand(EmailEigenschaften eigenschaften) {
        this(eigenschaften, smtp(eigenschaften.exchange(), eigenschaften.absender()));
    }

    private ExchangeEmailVersand(EmailEigenschaften eigenschaften, JavaMailSenderImpl smtp) {
        super(smtp, eigenschaften);
        this.exchange = eigenschaften.exchange();
        this.smtp = smtp;
        if (eigenschaften.enabled()) {
            pruefeEinstellungen(eigenschaften);
        }
    }

    @Override
    public String name() {
        return "Exchange";
    }

    @Override
    public void senden(VorbereiteteEmail email) throws MessagingException, UnsupportedEncodingException {
        MimeMessage nachricht = nachricht(email);
        smtp.send(nachricht);
        if (exchange.imGesendetSpeichern()) {
            inGesendetAblegen(nachricht);
        }
    }

    /** Legt die verschickte Mail per IMAP als gelesen in den Ordner {@code gesendetOrdner}. */
    void inGesendetAblegen(MimeMessage nachricht) {
        String ordnerName = exchange.gesendetOrdner().trim();
        try (Store store = imapSession().getStore(imapProtokoll())) {
            store.connect(imapHost(), exchange.imapPort(), exchange.benutzername(), exchange.passwort());
            Folder ordner = store.getFolder(ordnerName);
            if (!ordner.exists()) {
                throw new MessagingException("den Ordner gibt es im Postfach nicht (app.mail.exchange.gesendet-ordner, "
                        + "bei englischem Exchange \"Sent Items\")");
            }
            nachricht.setFlag(Flags.Flag.SEEN, true);
            ordner.appendMessages(new Message[]{nachricht});
        } catch (MessagingException e) {
            // Die Mail ist schon raus – deshalb nur warnen, sonst wuerde sie als nicht verschickt gelten
            log.warn("E-Mail \"{}\" wurde verschickt, aber die Kopie in \"{}\" per IMAP ist fehlgeschlagen: {}",
                    betreff(nachricht), ordnerName, e.getMessage());
        }
    }

    // ------------------------------------------------------------------ Verbindungen

    private static JavaMailSenderImpl smtp(EmailEigenschaften.Exchange exchange, String absender) {
        JavaMailSenderImpl sender = new JavaMailSenderImpl();
        String protokoll = exchange.ssl() ? "smtps" : "smtp";
        sender.setProtocol(protokoll);
        sender.setHost(leer(exchange.smtpHost()) ? null : exchange.smtpHost().trim());
        sender.setPort(exchange.smtpPort());
        sender.setUsername(exchange.benutzername());
        sender.setPassword(exchange.passwort());
        sender.setDefaultEncoding(StandardCharsets.UTF_8.name());

        Properties optionen = sender.getJavaMailProperties();
        String p = "mail." + protokoll + ".";
        optionen.setProperty(p + "auth", "true");
        if (!exchange.ssl() && exchange.starttls()) {
            optionen.setProperty(p + "starttls.enable", "true");
            optionen.setProperty(p + "starttls.required", "true");
        }
        if (!leer(exchange.ehloName())) {
            optionen.setProperty(p + "localhost", exchange.ehloName().trim());
        }
        zeitlimitUndVertrauen(optionen, p, exchange);
        ohneDnsAbfrage(optionen, protokoll, absender);
        return sender;
    }

    private Session imapSession() {
        Properties optionen = new Properties();
        String p = "mail." + imapProtokoll() + ".";
        if (!exchange.imapSsl()) {
            optionen.setProperty(p + "starttls.enable", "true");
            optionen.setProperty(p + "starttls.required", "true");
        }
        zeitlimitUndVertrauen(optionen, p, exchange);
        return Session.getInstance(optionen);
    }

    private static void zeitlimitUndVertrauen(Properties optionen, String p, EmailEigenschaften.Exchange exchange) {
        String zeitlimit = String.valueOf(exchange.timeoutMs());
        optionen.setProperty(p + "connectiontimeout", zeitlimit);
        optionen.setProperty(p + "timeout", zeitlimit);
        optionen.setProperty(p + "writetimeout", zeitlimit);
        if (!leer(exchange.sslTrust())) {
            optionen.setProperty(p + "ssl.trust", exchange.sslTrust().trim());
        }
        optionen.setProperty(p + "ssl.checkserveridentity", String.valueOf(exchange.sslServernamePruefen()));
    }

    private String imapProtokoll() {
        return exchange.imapSsl() ? "imaps" : "imap";
    }

    /** IMAP-Server – standardmaessig derselbe wie fuer SMTP. */
    String imapHost() {
        return leer(exchange.imapHost()) ? exchange.smtpHost().trim() : exchange.imapHost().trim();
    }

    private void pruefeEinstellungen(EmailEigenschaften eigenschaften) {
        List<String> fehlend = new ArrayList<>();
        if (leer(exchange.smtpHost())) {
            fehlend.add("app.mail.exchange.smtp-host (MAIL_EXCHANGE_SMTP_HOST)");
        }
        if (leer(exchange.benutzername())) {
            fehlend.add("app.mail.exchange.benutzername (MAIL_EXCHANGE_BENUTZERNAME)");
        }
        if (leer(exchange.passwort())) {
            fehlend.add("app.mail.exchange.passwort (MAIL_EXCHANGE_PASSWORT)");
        }
        if (leer(eigenschaften.absender())) {
            fehlend.add("app.mail.absender (MAIL_ABSENDER)");
        }
        if (exchange.imGesendetSpeichern() && leer(exchange.gesendetOrdner())) {
            fehlend.add("app.mail.exchange.gesendet-ordner (MAIL_EXCHANGE_GESENDET_ORDNER)");
        }
        if (!fehlend.isEmpty()) {
            throw new IllegalStateException("E-Mail-Versand per Exchange (app.mail.versandart=exchange) ist eingeschaltet, "
                    + "aber es fehlt: " + String.join(", ", fehlend)
                    + ". Werte setzen oder mit MAIL_VERSANDART=smtp bzw. MAIL_ENABLED=false starten.");
        }
    }

    private static String betreff(MimeMessage nachricht) {
        try {
            return nachricht.getSubject();
        } catch (MessagingException e) {
            return "?";
        }
    }

    private static boolean leer(String s) {
        return s == null || s.isBlank();
    }
}
