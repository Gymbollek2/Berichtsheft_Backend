package de.fisp.azubiprojekt.service;

import de.fisp.azubiprojekt.config.EmailEigenschaften;
import de.fisp.azubiprojekt.entity.Person;
import jakarta.mail.MessagingException;
import jakarta.mail.internet.InternetAddress;
import jakarta.mail.internet.MimeMessage;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.core.io.ClassPathResource;
import org.springframework.mail.MailException;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.mail.javamail.MimeMessageHelper;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import org.springframework.web.util.HtmlUtils;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.text.Normalizer;
import java.util.Locale;
import java.util.concurrent.Executor;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Verschickt E-Mails per SMTP (Server: {@code spring.mail.*}, Absender und Empfaenger: {@code app.mail.*}).
 * Jede Mail geht als HTML mit Button ins Portal und zusaetzlich als reiner Text raus.
 * Fehler beim Versand werden nur protokolliert – sie duerfen keine fachliche Aktion scheitern lassen.
 */
@Service
public class EmailService {

    private static final Logger log = LoggerFactory.getLogger(EmailService.class);
    private static final Pattern FETT = Pattern.compile("\\*\\*(.+?)\\*\\*");
    private static final String SCHRIFT = "font-family:'IBM Plex Sans','Segoe UI',Arial,sans-serif;";

    /** Fertig aufbereitete Mail – enthaelt nur noch Text, damit sie ausserhalb der Transaktion verschickt werden kann. */
    record VorbereiteteEmail(String an, String anName, String betreff, String text, String html) {
    }

    private final JavaMailSender mailSender;
    private final EmailEigenschaften eigenschaften;
    private final Executor executor;
    private final String vorlage;

    public EmailService(JavaMailSender mailSender, EmailEigenschaften eigenschaften,
                        @Qualifier("applicationTaskExecutor") Executor executor) {
        this.mailSender = mailSender;
        this.eigenschaften = eigenschaften;
        this.executor = executor;
        this.vorlage = ladeVorlage();
    }

    public boolean isAktiv() {
        return eigenschaften.enabled();
    }

    /**
     * Verschickt die Mail sofort und wartet auf den Mailserver (fuer Hintergrund-Jobs).
     *
     * @return true, wenn der Mailserver die Mail angenommen hat
     */
    public boolean senden(Person empfaenger, EmailInhalt inhalt) {
        if (!isAktiv()) {
            return false;
        }
        return versenden(vorbereiten(empfaenger, inhalt));
    }

    /**
     * Verschickt die Mail im Hintergrund, sobald die laufende Transaktion erfolgreich abgeschlossen ist –
     * wird die Aktion zurueckgerollt, geht auch keine Mail raus. Die Anfrage wartet nicht auf den Mailserver.
     */
    public void sendenNachAbschluss(Person empfaenger, EmailInhalt inhalt) {
        if (!isAktiv()) {
            return;
        }
        VorbereiteteEmail email = vorbereiten(empfaenger, inhalt);
        if (TransactionSynchronizationManager.isSynchronizationActive()) {
            TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
                @Override
                public void afterCommit() {
                    executor.execute(() -> versenden(email));
                }
            });
        } else {
            executor.execute(() -> versenden(email));
        }
    }

    // ------------------------------------------------------------------ Empfaenger

    /** Adresse nach dem Muster aus {@code app.mail.empfaenger.*}, z. B. Max.Mustermann@Azubi.de. */
    String adresse(Person person) {
        EmailEigenschaften.Empfaenger e = eigenschaften.empfaenger();
        String muster = person.getRole() != null && person.getRole().isStaff() ? e.musterAusbilder() : e.musterAzubi();
        String benutzername = person.getUsers() == null ? "" : person.getUsers().getUsername();
        // Windows-Konten stehen evtl. als DOMAENE\benutzer in der Personenliste
        benutzername = benutzername.substring(benutzername.lastIndexOf('\\') + 1);
        String adresse = muster
                .replace("{vorname}", adressteil(person.getFirstName()))
                .replace("{nachname}", adressteil(person.getLastName()))
                .replace("{benutzername}", adressteil(benutzername));
        return e.kleinschreibung() ? adresse.toLowerCase(Locale.ROOT) : adresse;
    }

    /** Umlaute umschreiben, Akzente entfernen, Leerzeichen zu Bindestrichen: "Jürgen Weiß" -> "Juergen-Weiss". */
    static String adressteil(String name) {
        if (name == null) {
            return "";
        }
        String s = name.trim()
                .replace("ä", "ae").replace("ö", "oe").replace("ü", "ue")
                .replace("Ä", "Ae").replace("Ö", "Oe").replace("Ü", "Ue").replace("ß", "ss");
        s = Normalizer.normalize(s, Normalizer.Form.NFD).replaceAll("\\p{M}", "");
        return s.replaceAll("\\s+", "-").replaceAll("[^A-Za-z0-9._-]", "");
    }

    // ------------------------------------------------------------------ Aufbereiten

    VorbereiteteEmail vorbereiten(Person empfaenger, EmailInhalt inhalt) {
        String praefix = eigenschaften.betreffPraefix();
        String betreff = (praefix == null || praefix.isBlank() ? "" : praefix.trim() + " ") + inhalt.getBetreff();
        return new VorbereiteteEmail(adresse(empfaenger), empfaenger.getFullName(), betreff,
                alsText(inhalt), alsHtml(inhalt, betreff));
    }

    private String alsHtml(EmailInhalt inhalt, String betreff) {
        StringBuilder html = new StringBuilder();
        for (EmailInhalt.Block block : inhalt.getBloecke()) {
            switch (block) {
                case EmailInhalt.Absatz a -> html.append("<p style=\"margin:0 0 16px 0;font-size:15px;line-height:24px;color:#1d1b3a;\">")
                        .append(html(a.text())).append("</p>");
                case EmailInhalt.Liste l -> {
                    html.append("<table role=\"presentation\" cellpadding=\"0\" cellspacing=\"0\" border=\"0\" width=\"100%\" style=\"margin:0 0 16px 0;\">");
                    for (String punkt : l.punkte()) {
                        html.append("<tr><td width=\"20\" valign=\"top\" style=\"").append(SCHRIFT)
                                .append("font-size:15px;line-height:24px;color:#a3126f;\">&#9679;</td>")
                                .append("<td style=\"").append(SCHRIFT).append("font-size:15px;line-height:24px;color:#1d1b3a;padding-bottom:6px;\">")
                                .append(html(punkt)).append("</td></tr>");
                    }
                    html.append("</table>");
                }
                case EmailInhalt.Zitat z -> html.append("<table role=\"presentation\" cellpadding=\"0\" cellspacing=\"0\" border=\"0\" width=\"100%\" style=\"margin:0 0 16px 0;\">")
                        .append("<tr><td style=\"").append(SCHRIFT)
                        .append("background-color:#f5f5f8;border-left:4px solid #a3126f;border-radius:6px;padding:14px 18px;\">")
                        .append("<div style=\"font-size:12px;line-height:18px;font-weight:600;text-transform:uppercase;letter-spacing:0.5px;color:#5d5b74;margin-bottom:6px;\">")
                        .append(html(z.titel())).append("</div>")
                        .append("<div style=\"font-size:15px;line-height:24px;color:#1d1b3a;white-space:pre-line;\">")
                        .append(html(z.text())).append("</div></td></tr></table>");
            }
        }

        String status = "";
        if (inhalt.getStatus() != null) {
            EmailInhalt.Ton ton = inhalt.getTon();
            status = "<span style=\"display:inline-block;margin:0 0 14px 0;padding:4px 12px;border-radius:999px;font-size:12px;"
                    + "line-height:18px;font-weight:600;color:" + ton.farbe + ";background-color:" + ton.hintergrund + ";\">"
                    + html(inhalt.getStatus()) + "</span>";
        }

        String button = "";
        String linkHinweis = "";
        if (inhalt.getButtonText() != null) {
            String url = esc(link(inhalt.getButtonPfad()));
            // "Bulletproof"-Button: Tabelle mit Hintergrundfarbe, funktioniert auch in Outlook
            button = "<table role=\"presentation\" cellpadding=\"0\" cellspacing=\"0\" border=\"0\"><tr>"
                    + "<td align=\"center\" bgcolor=\"#a3126f\" style=\"border-radius:6px;background-color:#a3126f;\">"
                    + "<a href=\"" + url + "\" target=\"_blank\" style=\"display:inline-block;padding:13px 28px;" + SCHRIFT
                    + "font-size:15px;line-height:20px;font-weight:600;color:#ffffff;text-decoration:none;border-radius:6px;\">"
                    + esc(inhalt.getButtonText()) + " &rarr;</a></td></tr></table>";
            linkHinweis = "<br>Falls der Button nicht funktioniert, kopiere diesen Link in deinen Browser:<br>"
                    + "<a href=\"" + url + "\" style=\"color:#10024d;word-break:break-all;\">" + url + "</a>";
        }

        return vorlage
                .replace("{{betreff}}", esc(betreff))
                .replace("{{vorschau}}", esc(vorschau(inhalt)))
                .replace("{{status}}", status)
                .replace("{{ueberschrift}}", esc(inhalt.getUeberschrift()))
                .replace("{{button}}", button)
                .replace("{{linkHinweis}}", linkHinweis)
                .replace("{{inhalt}}", html.toString());
    }

    private String alsText(EmailInhalt inhalt) {
        StringBuilder text = new StringBuilder(inhalt.getUeberschrift()).append("\n\n");
        for (EmailInhalt.Block block : inhalt.getBloecke()) {
            switch (block) {
                case EmailInhalt.Absatz a -> text.append(ohneMarkierung(a.text())).append("\n\n");
                case EmailInhalt.Liste l -> {
                    l.punkte().forEach(p -> text.append("  - ").append(ohneMarkierung(p)).append('\n'));
                    text.append('\n');
                }
                case EmailInhalt.Zitat z -> text.append(z.titel()).append(":\n  „").append(z.text()).append("“\n\n");
            }
        }
        if (inhalt.getButtonText() != null) {
            text.append(inhalt.getButtonText()).append(": ").append(link(inhalt.getButtonPfad())).append("\n\n");
        }
        return text.append("Viele Grüße\ndein Azubi Portal\n\n--\nDiese E-Mail wurde automatisch vom Azubi Portal verschickt.\n")
                .toString();
    }

    /** Erster Absatz als Vorschau im Posteingang. */
    private static String vorschau(EmailInhalt inhalt) {
        return inhalt.getBloecke().stream()
                .filter(EmailInhalt.Absatz.class::isInstance)
                .map(b -> ohneMarkierung(((EmailInhalt.Absatz) b).text()))
                .filter(t -> !t.startsWith("Hallo "))
                .findFirst().orElse("");
    }

    /** Escapt den Text und macht aus **...** eine Hervorhebung. */
    private static String html(String text) {
        Matcher m = FETT.matcher(esc(text));
        return m.replaceAll("<strong style=\"color:#010046;\">$1</strong>");
    }

    /** Escapt nur die HTML-Sonderzeichen – Umlaute bleiben lesbar (die Mail ist UTF-8). */
    private static String esc(String text) {
        return HtmlUtils.htmlEscape(text, StandardCharsets.UTF_8.name());
    }

    private static String ohneMarkierung(String text) {
        return FETT.matcher(text).replaceAll("$1");
    }

    private String link(String pfad) {
        String basis = eigenschaften.portalUrl().replaceAll("/+$", "");
        return basis + (pfad.startsWith("/") ? pfad : "/" + pfad);
    }

    // ------------------------------------------------------------------ Versand

    private boolean versenden(VorbereiteteEmail email) {
        String umleiten = eigenschaften.empfaenger().umleitenAn();
        boolean umgeleitet = umleiten != null && !umleiten.isBlank();
        String an = umgeleitet ? umleiten.trim() : email.an();
        try {
            MimeMessage nachricht = mailSender.createMimeMessage();
            MimeMessageHelper helper = new MimeMessageHelper(nachricht, MimeMessageHelper.MULTIPART_MODE_MIXED_RELATED,
                    StandardCharsets.UTF_8.name());
            helper.setFrom(new InternetAddress(eigenschaften.absender(), eigenschaften.absenderName(), StandardCharsets.UTF_8.name()));
            helper.setTo(new InternetAddress(an, umgeleitet ? null : email.anName(), StandardCharsets.UTF_8.name()));
            if (eigenschaften.antwortAn() != null && !eigenschaften.antwortAn().isBlank()) {
                helper.setReplyTo(eigenschaften.antwortAn().trim());
            }
            if (umgeleitet) {
                nachricht.setHeader("X-Original-To", email.an());
            }
            helper.setSubject(email.betreff());
            helper.setText(email.text(), email.html());
            mailSender.send(nachricht);
            log.info("E-Mail \"{}\" an {} verschickt{}", email.betreff(), an,
                    umgeleitet ? " (umgeleitet, eigentlich an " + email.an() + ")" : "");
            return true;
        } catch (MailException | MessagingException | IOException e) {
            log.warn("E-Mail \"{}\" an {} konnte nicht verschickt werden: {}", email.betreff(), an, e.getMessage());
            return false;
        }
    }

    private static String ladeVorlage() {
        try (var in = new ClassPathResource("mail/vorlage.html").getInputStream()) {
            return new String(in.readAllBytes(), StandardCharsets.UTF_8);
        } catch (IOException e) {
            throw new UncheckedIOException("E-Mail-Vorlage mail/vorlage.html fehlt", e);
        }
    }
}
