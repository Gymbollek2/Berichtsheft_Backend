package de.fisp.azubiprojekt.service;

import de.fisp.azubiprojekt.dao.BenachrichtigungDAO;
import de.fisp.azubiprojekt.dao.PersonDAO;
import de.fisp.azubiprojekt.dao.WochenberichtDAO;
import de.fisp.azubiprojekt.dto.response.BenachrichtigungDTO;
import de.fisp.azubiprojekt.dto.response.BenachrichtigungenResponseDTO;
import de.fisp.azubiprojekt.entity.Benachrichtigung;
import de.fisp.azubiprojekt.entity.Person;
import de.fisp.azubiprojekt.entity.Wochenbericht;
import de.fisp.azubiprojekt.exceptions.ResourceNotFoundException;
import de.fisp.azubiprojekt.util.BenachrichtigungTypEnum;
import de.fisp.azubiprojekt.util.BerichtStatusEnum;
import de.fisp.azubiprojekt.util.RoleEnum;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.data.domain.PageRequest;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.util.List;
import java.util.Objects;

/**
 * Benachrichtigungen (Glocke oben rechts): verschicken, lesen und als gelesen markieren.
 * Erzeugt ausserdem taeglich Erinnerungen an noch nicht abgegebene Berichtshefte der Vorwoche.
 */
@Service
public class BenachrichtigungService {

    private static final Logger log = LoggerFactory.getLogger(BenachrichtigungService.class);
    /** So viele Nachrichten zeigt die Glocke hoechstens an. */
    static final int ANZAHL_ANZEIGE = 30;

    private final BenachrichtigungDAO benachrichtigungDAO;
    private final PersonDAO personDAO;
    private final WochenberichtDAO wochenberichtDAO;
    private final CurrentUserService currentUserService;
    private final boolean erinnerungen;

    public BenachrichtigungService(BenachrichtigungDAO benachrichtigungDAO, PersonDAO personDAO,
                                   WochenberichtDAO wochenberichtDAO, CurrentUserService currentUserService,
                                   @Value("${app.erinnerungen.enabled:true}") boolean erinnerungen) {
        this.benachrichtigungDAO = benachrichtigungDAO;
        this.personDAO = personDAO;
        this.wochenberichtDAO = wochenberichtDAO;
        this.currentUserService = currentUserService;
        this.erinnerungen = erinnerungen;
    }

    // ------------------------------------------------------------------ Eigene Nachrichten

    @Transactional(readOnly = true)
    public BenachrichtigungenResponseDTO getEigene() {
        Person ich = currentUserService.getPerson();
        List<BenachrichtigungDTO> eintraege = benachrichtigungDAO
                .findByEmpfaengerOrderByErstelltAmDescIdDesc(ich, PageRequest.of(0, ANZAHL_ANZEIGE)).stream()
                .map(BenachrichtigungDTO::from)
                .toList();
        return new BenachrichtigungenResponseDTO(benachrichtigungDAO.countByEmpfaengerAndGelesenFalse(ich), eintraege);
    }

    @Transactional(readOnly = true)
    public long getAnzahlUngelesen() {
        return benachrichtigungDAO.countByEmpfaengerAndGelesenFalse(currentUserService.getPerson());
    }

    @Transactional
    public BenachrichtigungDTO markiereGelesen(Long id) {
        Benachrichtigung b = benachrichtigungDAO.findById(id)
                .filter(n -> Objects.equals(n.getEmpfaenger().getId(), currentUserService.getPersonId()))
                // Fremde Nachrichten werden wie nicht vorhandene behandelt
                .orElseThrow(() -> new ResourceNotFoundException("Benachrichtigung " + id + " nicht gefunden"));
        b.setGelesen(true);
        return BenachrichtigungDTO.from(b);
    }

    @Transactional
    public void markiereAlleGelesen() {
        benachrichtigungDAO.markiereAlleGelesen(currentUserService.getPerson());
    }

    // ------------------------------------------------------------------ Verschicken

    @Transactional
    public void senden(Person empfaenger, BenachrichtigungTypEnum typ, String titel, String text, String link, String bezug) {
        benachrichtigungDAO.save(new Benachrichtigung(empfaenger, typ, titel, text, link, bezug));
    }

    /** An alle Ausbilder – ausser an den, der die Aktion selbst ausgeloest hat. */
    @Transactional
    public void sendenAnAusbilder(BenachrichtigungTypEnum typ, String titel, String text, String link, String bezug) {
        Long ausloeser = currentUserService.getPersonId();
        for (Person ausbilder : personDAO.findByRole(RoleEnum.ROLE_TRAINER)) {
            if (!Objects.equals(ausbilder.getId(), ausloeser)) {
                senden(ausbilder, typ, titel, text, link, bezug);
            }
        }
    }

    /** Der Anlass ist erledigt (z. B. Woche wurde geprueft): zugehoerige Nachrichten gelten bei allen als gelesen. */
    @Transactional
    public void erledigt(String bezug, BenachrichtigungTypEnum typ) {
        benachrichtigungDAO.markiereErledigt(bezug, typ);
    }

    @Transactional
    public void erledigt(Person empfaenger, String bezug, BenachrichtigungTypEnum typ) {
        benachrichtigungDAO.markiereErledigt(empfaenger, bezug, typ);
    }

    // ------------------------------------------------------------------ Erinnerungen

    /** Taeglich um 7 Uhr – und einmal nach dem Start, falls der Server um 7 Uhr nicht lief. */
    @Scheduled(cron = "${app.erinnerungen.cron:0 0 7 * * *}")
    @EventListener(ApplicationReadyEvent.class)
    public void erinnerungenPlanmaessig() {
        if (!erinnerungen) {
            return;
        }
        try {
            int anzahl = erinnerungenErzeugen(LocalDate.now());
            if (anzahl > 0) {
                log.info("{} Erinnerung(en) an nicht abgegebene Berichtshefte verschickt", anzahl);
            }
        } catch (RuntimeException e) {
            log.warn("Erinnerungen konnten nicht erzeugt werden: {}", e.getMessage());
        }
    }

    /**
     * Erinnert alle Azubis, deren Berichtsheft fuer die Vorwoche (bezogen auf {@code heute}) noch nicht abgegeben ist.
     * Je Person und Woche gibt es hoechstens eine Erinnerung. Zurueckgegebene Wochen haben schon eine eigene Nachricht.
     *
     * @return Anzahl neuer Erinnerungen
     */
    @Transactional
    public int erinnerungenErzeugen(LocalDate heute) {
        LocalDate vorwoche = BerichtsheftService.weekStart(heute).minusWeeks(1);
        LocalDate freitag = vorwoche.plusDays(4);
        String woche = "KW " + BerichtsheftService.kalenderwoche(vorwoche) + "/" + BerichtsheftService.wochenJahr(vorwoche);
        int anzahl = 0;
        for (Person azubi : personDAO.findByRoleIn(List.of(RoleEnum.ROLE_TRAINEE, RoleEnum.ROLE_STUDENT))) {
            if (azubi.getDateOfTrainingStart() != null && azubi.getDateOfTrainingStart().isAfter(freitag)) {
                continue;
            }
            BerichtStatusEnum status = wochenberichtDAO.findByPersonAndWeekStart(azubi, vorwoche)
                    .map(Wochenbericht::getStatus)
                    .orElse(BerichtStatusEnum.ENTWURF);
            String bezug = erinnerungsBezug(vorwoche);
            if (status != BerichtStatusEnum.ENTWURF || benachrichtigungDAO.existsByEmpfaengerAndBezug(azubi, bezug)) {
                continue;
            }
            senden(azubi, BenachrichtigungTypEnum.ERINNERUNG_ABGABE, "Berichtsheft noch nicht abgegeben",
                    "Dein Berichtsheft für " + woche + " ist noch nicht abgegeben. Bitte vervollständige es und gib es ab.",
                    "/berichtsheft?datum=" + vorwoche, bezug);
            anzahl++;
        }
        return anzahl;
    }

    static String erinnerungsBezug(LocalDate weekStart) {
        return "erinnerung:" + weekStart;
    }
}
