package de.fisp.azubiprojekt.service;

import de.fisp.azubiprojekt.config.EmailEigenschaften;
import de.fisp.azubiprojekt.dao.PersonDAO;
import de.fisp.azubiprojekt.dao.VersendeteEmailDAO;
import de.fisp.azubiprojekt.dao.WochenberichtDAO;
import de.fisp.azubiprojekt.entity.Person;
import de.fisp.azubiprojekt.entity.VersendeteEmail;
import de.fisp.azubiprojekt.entity.Wochenbericht;
import de.fisp.azubiprojekt.util.BerichtStatusEnum;
import de.fisp.azubiprojekt.util.EmailAnlassEnum;
import de.fisp.azubiprojekt.util.RoleEnum;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.time.temporal.ChronoUnit;
import java.util.*;
import java.util.stream.Collectors;

/**
 * Fachliche E-Mails rund ums Berichtsheft:
 * <ul>
 *     <li>an die Person, wenn ihr Account angelegt wurde,</li>
 *     <li>an den Azubi, wenn eine Woche abgenommen oder zur Ueberarbeitung zurueckgegeben wurde,</li>
 *     <li>an den Azubi, wenn eine Woche seit ueber einer Woche faellig, aber nicht abgegeben ist,</li>
 *     <li>an die Ausbilder, wenn eine abgegebene Woche seit ueber einer Woche nicht geprueft wurde.</li>
 * </ul>
 * Erinnerungen gehen je Empfaenger und Woche nur einmal raus; mehrere offene Wochen werden in einer Mail gesammelt.
 */
@Service
public class EmailBenachrichtigungService {

    private static final Logger log = LoggerFactory.getLogger(EmailBenachrichtigungService.class);
    private static final DateTimeFormatter TAG_MONAT = DateTimeFormatter.ofPattern("dd.MM.");
    private static final DateTimeFormatter DATUM = DateTimeFormatter.ofPattern("dd.MM.yyyy");

    private final EmailService emailService;
    private final EmailEigenschaften eigenschaften;
    private final PersonDAO personDAO;
    private final WochenberichtDAO wochenberichtDAO;
    private final VersendeteEmailDAO versendeteEmailDAO;
    private final TransactionTemplate transaktion;

    public EmailBenachrichtigungService(EmailService emailService, EmailEigenschaften eigenschaften, PersonDAO personDAO,
                                        WochenberichtDAO wochenberichtDAO, VersendeteEmailDAO versendeteEmailDAO,
                                        PlatformTransactionManager transactionManager) {
        this.emailService = emailService;
        this.eigenschaften = eigenschaften;
        this.personDAO = personDAO;
        this.wochenberichtDAO = wochenberichtDAO;
        this.versendeteEmailDAO = versendeteEmailDAO;
        this.transaktion = new TransactionTemplate(transactionManager);
    }

    // ------------------------------------------------------------------ Ergebnis der Pruefung

    public void berichtAbgenommen(Wochenbericht bericht) {
        Person azubi = bericht.getPerson();
        LocalDate ws = bericht.getWeekStart();
        EmailInhalt inhalt = EmailInhalt.mitBetreff("Dein Berichtsheft für " + woche(ws) + " wurde abgenommen")
                .ueberschrift("Berichtsheft abgenommen")
                .status("Abgenommen", EmailInhalt.Ton.ERFOLG)
                .absatz("Hallo " + azubi.getFirstName() + ",")
                .absatz("gute Nachrichten: **" + bericht.getGeprueftVon() + "** hat dein Berichtsheft für **"
                        + wocheMitZeitraum(ws) + "** geprüft und abgenommen. Für diese Woche ist damit alles erledigt.");
        if (bericht.getKommentar() != null) {
            inhalt.zitat("Kommentar von " + bericht.getGeprueftVon(), bericht.getKommentar());
        }
        inhalt.absatz("Danke, dass du dein Berichtsheft so zuverlässig führst – weiter so!")
                .button("Woche ansehen", "/berichtsheft?datum=" + ws);
        emailService.sendenNachAbschluss(azubi, inhalt);
    }

    public void berichtZurueckgegeben(Wochenbericht bericht) {
        Person azubi = bericht.getPerson();
        LocalDate ws = bericht.getWeekStart();
        EmailInhalt inhalt = EmailInhalt.mitBetreff("Dein Berichtsheft für " + woche(ws) + " braucht eine Überarbeitung")
                .ueberschrift("Bitte überarbeite dein Berichtsheft")
                .status("Zur Überarbeitung", EmailInhalt.Ton.KORREKTUR)
                .absatz("Hallo " + azubi.getFirstName() + ",")
                .absatz("**" + bericht.getGeprueftVon() + "** hat dein Berichtsheft für **" + wocheMitZeitraum(ws)
                        + "** geprüft und zur Überarbeitung an dich zurückgegeben.")
                .zitat("Das soll überarbeitet werden", bericht.getKommentar())
                .absatz("Bitte passe deine Einträge entsprechend an und gib die Woche danach erneut ab. "
                        + "Bei Fragen wende dich einfach an " + bericht.getGeprueftVon() + ".")
                .button("Berichtsheft überarbeiten", "/berichtsheft?datum=" + ws);
        emailService.sendenNachAbschluss(azubi, inhalt);
    }

    // ------------------------------------------------------------------ Neuer Account

    /**
     * Teilt der Person mit, dass ihr Zugang angelegt wurde. Das Startpasswort steht bewusst nicht in der Mail.
     *
     * @param angelegtVon            Name der Person, die den Account angelegt hat
     * @param benutzernameAngegeben  false, wenn der Benutzername automatisch erzeugt wurde – dann geht die Mail an
     *                               Vorname.Nachname@…, sonst an benutzername@…
     */
    public void accountErstellt(Person person, String angelegtVon, boolean benutzernameAngegeben) {
        EmailInhalt inhalt = EmailInhalt.mitBetreff("Dein Zugang zum Azubi Portal wurde erstellt")
                .ueberschrift("Willkommen im Azubi Portal")
                .status("Account erstellt", EmailInhalt.Ton.ERFOLG)
                .absatz("Hallo " + person.getFirstName() + ",")
                .absatz("**" + angelegtVon + "** hat für dich einen Zugang zum Azubi Portal angelegt. "
                        + "Ab sofort kannst du dich mit folgenden Daten anmelden:")
                .liste(List.of("Benutzername: **" + person.getUsers().getUsername() + "**",
                        "Rolle: **" + person.getRole().getLabel() + "**"))
                .absatz("Dein Startpasswort bekommst du von " + angelegtVon + ". Bitte ändere es nach der ersten "
                        + "Anmeldung unter „Mein Profil“.")
                .button("Zum Azubi Portal", "/profil");
        emailService.sendenNachAbschluss(person, emailService.kontoAdresse(person, benutzernameAngegeben), inhalt);
    }

    // ------------------------------------------------------------------ Erinnerungen

    /** Taeglich (Standard 7:30 Uhr) – und einmal nach dem Start, falls der Server zur geplanten Zeit nicht lief. */
    @Scheduled(cron = "${app.mail.erinnerung.cron:0 30 7 * * *}")
    @EventListener(ApplicationReadyEvent.class)
    public void erinnerungenPlanmaessig() {
        if (!emailService.isAktiv() || !eigenschaften.erinnerung().enabled()) {
            return;
        }
        try {
            Integer azubis = transaktion.execute(s -> erinnerungenAnAzubis(LocalDate.now()));
            Integer ausbilder = transaktion.execute(s -> erinnerungenAnAusbilder(LocalDateTime.now()));
            if (azubis + ausbilder > 0) {
                log.info("Erinnerungs-E-Mails verschickt: {} an Azubis, {} an Ausbilder", azubis, ausbilder);
            }
        } catch (RuntimeException e) {
            log.warn("Erinnerungs-E-Mails konnten nicht verschickt werden: {}", e.getMessage());
        }
    }

    /**
     * Erinnert Azubis an Wochen, die seit mehr als {@code tageBisAbgabe} Tagen (ab Freitag der Woche) faellig,
     * aber noch nicht abgegeben sind. Zurueckgegebene Wochen haben schon eine eigene Mail bekommen.
     * Muss in einer Transaktion laufen.
     *
     * @return Anzahl verschickter Mails
     */
    int erinnerungenAnAzubis(LocalDate heute) {
        EmailEigenschaften.Erinnerung cfg = eigenschaften.erinnerung();
        LocalDate ersteWoche = BerichtsheftService.weekStart(heute).minusWeeks(cfg.maxWochenZurueck());
        int anzahl = 0;
        for (Person azubi : personDAO.findByRoleIn(List.of(RoleEnum.ROLE_TRAINEE, RoleEnum.ROLE_STUDENT))) {
            Map<LocalDate, BerichtStatusEnum> status = wochenberichtDAO.findByPersonOrderByWeekStartDesc(azubi).stream()
                    .collect(Collectors.toMap(Wochenbericht::getWeekStart, Wochenbericht::getStatus));
            List<LocalDate> offen = new ArrayList<>();
            for (LocalDate ws = ersteWoche; !heute.isBefore(faelligSeit(ws).plusDays(cfg.tageBisAbgabe())); ws = ws.plusWeeks(1)) {
                boolean vorBeginn = azubi.getDateOfTrainingStart() != null && azubi.getDateOfTrainingStart().isAfter(faelligSeit(ws));
                if (vorBeginn || status.getOrDefault(ws, BerichtStatusEnum.ENTWURF) != BerichtStatusEnum.ENTWURF
                        || versendeteEmailDAO.existsByPersonIdAndAnlassAndBezug(azubi.getId(), EmailAnlassEnum.ERINNERUNG_ABGABE, wochenBezug(ws))) {
                    continue;
                }
                offen.add(ws);
            }
            if (offen.isEmpty()) {
                continue;
            }
            if (emailService.senden(azubi, abgabeErinnerung(azubi, offen, cfg.tageBisAbgabe()))) {
                offen.forEach(ws -> versendeteEmailDAO.save(new VersendeteEmail(azubi.getId(), EmailAnlassEnum.ERINNERUNG_ABGABE, wochenBezug(ws))));
                anzahl++;
            }
        }
        return anzahl;
    }

    /**
     * Erinnert alle Ausbilder an Wochen, die seit mehr als {@code tageBisAbnahme} Tagen abgegeben, aber weder
     * abgenommen noch zurueckgegeben sind. Wird eine Woche erneut abgegeben, zaehlt die Frist von vorn.
     * Muss in einer Transaktion laufen.
     *
     * @return Anzahl verschickter Mails
     */
    int erinnerungenAnAusbilder(LocalDateTime jetzt) {
        int tage = eigenschaften.erinnerung().tageBisAbnahme();
        List<Wochenbericht> ueberfaellig = wochenberichtDAO.findByStatusOrderByEingereichtAmAsc(BerichtStatusEnum.EINGEREICHT).stream()
                .filter(w -> w.getEingereichtAm() != null && !w.getEingereichtAm().plusDays(tage).isAfter(jetzt))
                .toList();
        if (ueberfaellig.isEmpty()) {
            return 0;
        }
        int anzahl = 0;
        for (Person ausbilder : personDAO.findByRole(RoleEnum.ROLE_TRAINER)) {
            List<Wochenbericht> neu = ueberfaellig.stream()
                    .filter(w -> !versendeteEmailDAO.existsByPersonIdAndAnlassAndBezug(ausbilder.getId(), EmailAnlassEnum.ERINNERUNG_ABNAHME, abnahmeBezug(w)))
                    .toList();
            if (neu.isEmpty()) {
                continue;
            }
            if (emailService.senden(ausbilder, abnahmeErinnerung(ausbilder, neu, jetzt, tage))) {
                neu.forEach(w -> versendeteEmailDAO.save(new VersendeteEmail(ausbilder.getId(), EmailAnlassEnum.ERINNERUNG_ABNAHME, abnahmeBezug(w))));
                anzahl++;
            }
        }
        return anzahl;
    }

    private EmailInhalt abgabeErinnerung(Person azubi, List<LocalDate> wochen, int tage) {
        LocalDate aelteste = wochen.getFirst();
        boolean eine = wochen.size() == 1;
        EmailInhalt inhalt = EmailInhalt.mitBetreff(eine
                        ? "Erinnerung: Dein Berichtsheft für " + woche(aelteste) + " fehlt noch"
                        : "Erinnerung: " + wochen.size() + " Berichtshefte sind noch nicht abgegeben")
                .ueberschrift(eine ? "Dein Berichtsheft wartet auf dich" : "Deine Berichtshefte warten auf dich")
                .status("Abgabe überfällig", EmailInhalt.Ton.ERINNERUNG)
                .absatz("Hallo " + azubi.getFirstName() + ",");
        if (eine) {
            inhalt.absatz("dein Berichtsheft für **" + wocheMitZeitraum(aelteste) + "** ist seit über " + zeitraum(tage)
                    + " fällig, wurde aber noch nicht abgegeben.");
        } else {
            inhalt.absatz("für folgende Wochen ist dein Berichtsheft seit über " + zeitraum(tage)
                            + " fällig, wurde aber noch nicht abgegeben:")
                    .liste(wochen.stream().map(ws -> "**" + wocheMitZeitraum(ws) + "**").toList());
        }
        return inhalt
                .absatz("Bitte vervollständige die fehlenden Tage und gib " + (eine ? "die Woche" : "die Wochen")
                        + " zur Prüfung ab. Urlaub, Krankheit und Feiertage trägst du einfach als solche ein.")
                .button(eine ? "Berichtsheft öffnen" : "Älteste Woche öffnen", "/berichtsheft?datum=" + aelteste);
    }

    private EmailInhalt abnahmeErinnerung(Person ausbilder, List<Wochenbericht> berichte, LocalDateTime jetzt, int tage) {
        boolean einer = berichte.size() == 1;
        Wochenbericht erster = berichte.getFirst();
        EmailInhalt inhalt = EmailInhalt.mitBetreff(einer
                        ? "Berichtsheft von " + erster.getPerson().getFullName() + " wartet seit "
                                + wartetSeit(erster, jetzt) + " auf deine Prüfung"
                        : berichte.size() + " Berichtshefte warten seit über " + zeitraum(tage) + " auf Prüfung")
                .ueberschrift(einer ? "Ein Berichtsheft wartet auf deine Prüfung" : "Berichtshefte warten auf deine Prüfung")
                .status("Prüfung überfällig", EmailInhalt.Ton.ERINNERUNG)
                .absatz("Hallo " + ausbilder.getFirstName() + ",")
                .absatz(einer
                        ? "folgendes Berichtsheft wurde vor über " + zeitraum(tage) + " abgegeben und ist noch nicht geprüft:"
                        : "folgende Berichtshefte wurden vor über " + zeitraum(tage) + " abgegeben und sind noch nicht geprüft:")
                .liste(berichte.stream()
                        .map(w -> "**" + w.getPerson().getFullName() + "** – " + wocheMitZeitraum(w.getWeekStart())
                                + ", abgegeben am " + w.getEingereichtAm().format(DATUM) + " (vor " + wartetSeit(w, jetzt) + ")")
                        .toList())
                .absatz("Bitte nimm " + (einer ? "die Woche" : "die Wochen") + " ab oder gib sie mit einem Kommentar "
                        + "zur Überarbeitung zurück, damit " + (einer ? erster.getPerson().getFirstName() : "die Azubis")
                        + " zeitnah eine Rückmeldung " + (einer ? "bekommt." : "bekommen."));
        return einer
                ? inhalt.button("Berichtsheft prüfen", "/berichtsheft/azubis/" + erster.getPerson().getId() + "?datum=" + erster.getWeekStart())
                : inhalt.button("Zur Prüfübersicht", "/berichtsheft/pruefung");
    }

    // ------------------------------------------------------------------ Hilfsmethoden

    /** Eine Woche kann ab ihrem Freitag abgegeben werden – ab dann ist sie faellig. */
    private static LocalDate faelligSeit(LocalDate weekStart) {
        return weekStart.plusDays(4);
    }

    static String wochenBezug(LocalDate weekStart) {
        return "woche:" + weekStart;
    }

    static String abnahmeBezug(Wochenbericht w) {
        return "wochenbericht:" + w.getId() + ":" + w.getEingereichtAm().truncatedTo(ChronoUnit.SECONDS);
    }

    private static String woche(LocalDate ws) {
        return "KW " + BerichtsheftService.kalenderwoche(ws) + "/" + BerichtsheftService.wochenJahr(ws);
    }

    /** z. B. "KW 42/2026 (12.10. – 16.10.2026)" – Montag bis Freitag. */
    private static String wocheMitZeitraum(LocalDate ws) {
        return woche(ws) + " (" + ws.format(TAG_MONAT) + " – " + faelligSeit(ws).format(DATUM) + ")";
    }

    private static String zeitraum(int tage) {
        return tage == 7 ? "einer Woche" : tage == 1 ? "einem Tag" : tage + " Tagen";
    }

    private static String wartetSeit(Wochenbericht w, LocalDateTime jetzt) {
        long tage = ChronoUnit.DAYS.between(w.getEingereichtAm().toLocalDate(), jetzt.toLocalDate());
        return tage == 1 ? "einem Tag" : tage + " Tagen";
    }
}
