package de.fisp.azubiprojekt.service;

import de.fisp.azubiprojekt.config.EmailEigenschaften;
import jakarta.mail.MessagingException;
import jakarta.mail.internet.InternetAddress;
import jakarta.mail.internet.MimeMessage;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.mail.javamail.MimeMessageHelper;

import java.io.UnsupportedEncodingException;
import java.net.InetAddress;
import java.net.UnknownHostException;
import java.nio.charset.StandardCharsets;
import java.util.Properties;

/**
 * Versand per SMTP (Server: {@code spring.mail.*}) – z. B. an Mailhog oder einen eigenen Exchange-Server.
 * Die Mail geht als HTML und zusaetzlich als reiner Text raus.
 */
public class SmtpEmailVersand implements EmailVersand {

    private final JavaMailSender mailSender;
    private final EmailEigenschaften eigenschaften;

    public SmtpEmailVersand(JavaMailSender mailSender, EmailEigenschaften eigenschaften) {
        this.mailSender = mailSender;
        this.eigenschaften = eigenschaften;
    }

    /**
     * Ergaenzt die Optionen, ohne die Jakarta Mail den eigenen Rechnernamen per DNS rueckwaerts nachschlaegt – einmal
     * fuer den EHLO-Gruss, einmal fuer die Message-ID. Ohne passenden Rueckwaerts-Eintrag (z. B. am Arbeitsplatz-PC)
     * dauert das je Mail viele Sekunden. Bereits gesetzte Werte bleiben.
     *
     * @param protokoll "smtp" oder "smtps"
     * @param absender  Absenderadresse – ihre Domain kommt in die Message-ID
     */
    public static void ohneDnsAbfrage(Properties optionen, String protokoll, String absender) {
        String ehlo = "mail." + protokoll + ".localhost";
        if (leer(optionen.getProperty(ehlo))) {
            optionen.setProperty(ehlo, rechnername());
        }
        if (leer(optionen.getProperty("mail.from")) && !leer(absender)) {
            optionen.setProperty("mail.from", absender.trim());
        }
    }

    /** Name des eigenen Rechners, wie ihn das Betriebssystem kennt – ohne Rueckwaerts-Abfrage im DNS. */
    static String rechnername() {
        try {
            return InetAddress.getLocalHost().getHostName();
        } catch (UnknownHostException e) {
            return "localhost";
        }
    }

    private static boolean leer(String s) {
        return s == null || s.isBlank();
    }

    @Override
    public String name() {
        return "SMTP";
    }

    @Override
    public void senden(VorbereiteteEmail email) throws MessagingException, UnsupportedEncodingException {
        mailSender.send(nachricht(email));
    }

    /** Baut die MIME-Nachricht (HTML + Text) fuer den Versand. */
    MimeMessage nachricht(VorbereiteteEmail email) throws MessagingException, UnsupportedEncodingException {
        String utf8 = StandardCharsets.UTF_8.name();
        MimeMessage nachricht = mailSender.createMimeMessage();
        MimeMessageHelper helper = new MimeMessageHelper(nachricht, MimeMessageHelper.MULTIPART_MODE_MIXED_RELATED, utf8);
        helper.setFrom(new InternetAddress(eigenschaften.absender(), eigenschaften.absenderName(), utf8));
        helper.setTo(new InternetAddress(email.an(), email.anName(), utf8));
        if (eigenschaften.antwortAn() != null && !eigenschaften.antwortAn().isBlank()) {
            helper.setReplyTo(eigenschaften.antwortAn().trim());
        }
        if (email.istUmgeleitet()) {
            nachricht.setHeader("X-Original-To", email.urspruenglichAn());
        }
        helper.setSubject(email.betreff());
        helper.setText(email.text(), email.html());
        return nachricht;
    }
}
