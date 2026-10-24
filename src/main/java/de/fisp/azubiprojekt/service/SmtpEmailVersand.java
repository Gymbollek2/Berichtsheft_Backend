package de.fisp.azubiprojekt.service;

import de.fisp.azubiprojekt.config.EmailEigenschaften;
import jakarta.mail.MessagingException;
import jakarta.mail.internet.InternetAddress;
import jakarta.mail.internet.MimeMessage;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.mail.javamail.MimeMessageHelper;

import java.io.UnsupportedEncodingException;
import java.nio.charset.StandardCharsets;

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

    @Override
    public String name() {
        return "SMTP";
    }

    @Override
    public void senden(VorbereiteteEmail email) throws MessagingException, UnsupportedEncodingException {
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
        mailSender.send(nachricht);
    }
}
