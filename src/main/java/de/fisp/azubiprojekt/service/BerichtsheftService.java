package de.fisp.azubiprojekt.service;

import de.fisp.azubiprojekt.dao.BerichtsheftDAO;
import de.fisp.azubiprojekt.dao.LernzielDAO;
import de.fisp.azubiprojekt.dao.PersonLernzielDAO;
import de.fisp.azubiprojekt.dao.WochenberichtDAO;
import de.fisp.azubiprojekt.dto.request.BerichtsheftSaveRequestDTO;
import de.fisp.azubiprojekt.dto.response.BerichtsheftEintragDTO;
import de.fisp.azubiprojekt.dto.response.WochenTagDTO;
import de.fisp.azubiprojekt.dto.response.WochenberichtResponseDTO;
import de.fisp.azubiprojekt.dto.response.WochenberichtSummaryDTO;
import de.fisp.azubiprojekt.entity.Berichtsheft;
import de.fisp.azubiprojekt.entity.Lernziel;
import de.fisp.azubiprojekt.entity.Person;
import de.fisp.azubiprojekt.entity.Wochenbericht;
import de.fisp.azubiprojekt.exceptions.ActionNotAllowedException;
import de.fisp.azubiprojekt.exceptions.InvalidRequestException;
import de.fisp.azubiprojekt.exceptions.ResourceNotFoundException;
import de.fisp.azubiprojekt.util.BerichtStatusEnum;
import de.fisp.azubiprojekt.util.EintragArtEnum;
import de.fisp.azubiprojekt.util.KindEnum;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.time.format.TextStyle;
import java.time.temporal.ChronoUnit;
import java.time.temporal.WeekFields;
import java.util.*;
import java.util.stream.Collectors;

/**
 * Digitales Berichtsheft: Tageseintraege erfassen, Wochen einreichen und durch Ausbilder abzeichnen lassen.
 */
@Service
public class BerichtsheftService {

    static final double MAX_HOURS = 10;
    private static final DateTimeFormatter DE_DATE = DateTimeFormatter.ofPattern("dd.MM.yyyy");

    private final BerichtsheftDAO berichtsheftDAO;
    private final PersonLernzielDAO personLernzielDAO;
    private final WochenberichtDAO wochenberichtDAO;
    private final LernzielDAO lernzielDAO;
    private final CurrentUserService currentUserService;
    private final PersonLernzielService personLernzielService;

    public BerichtsheftService(BerichtsheftDAO berichtsheftDAO, WochenberichtDAO wochenberichtDAO, LernzielDAO lernzielDAO,
                               PersonLernzielDAO personLernzielDAO, CurrentUserService currentUserService,
                               PersonLernzielService personLernzielService) {
        this.berichtsheftDAO = berichtsheftDAO;
        this.personLernzielDAO = personLernzielDAO;
        this.wochenberichtDAO = wochenberichtDAO;
        this.lernzielDAO = lernzielDAO;
        this.currentUserService = currentUserService;
        this.personLernzielService = personLernzielService;
    }

    // ------------------------------------------------------------------ Lesen

    /** Komplette Woche (Mo–So), in der {@code datum} liegt. Ohne personId: eigene Woche. */
    @Transactional(readOnly = true)
    public WochenberichtResponseDTO getWoche(Long personId, LocalDate datum) {
        Person person = currentUserService.resolveAccessiblePerson(personId);
        return buildWoche(person, weekStart(datum == null ? LocalDate.now() : datum));
    }

    /** Alle zur Pruefung eingereichten Wochen (aelteste zuerst). */
    @Transactional(readOnly = true)
    public List<WochenberichtSummaryDTO> getOffen() {
        return wochenberichtDAO.findByStatusOrderByEingereichtAmAsc(BerichtStatusEnum.EINGEREICHT).stream()
                .map(this::summary)
                .toList();
    }

    /** Alle Wochen einer Person, in denen etwas erfasst wurde (neueste zuerst). */
    @Transactional(readOnly = true)
    public List<WochenberichtSummaryDTO> getHistorie(Long personId) {
        Person person = currentUserService.resolveAccessiblePerson(personId);

        Map<LocalDate, Wochenbericht> berichte = wochenberichtDAO.findByPersonOrderByWeekStartDesc(person).stream()
                .collect(Collectors.toMap(Wochenbericht::getWeekStart, w -> w));
        Map<LocalDate, List<Berichtsheft>> eintraege = person.getBerichtshefte().stream()
                .collect(Collectors.groupingBy(b -> weekStart(b.getDate())));

        Set<LocalDate> wochen = new TreeSet<>(Comparator.reverseOrder());
        wochen.addAll(berichte.keySet());
        wochen.addAll(eintraege.keySet());

        return wochen.stream().map(ws -> {
            Wochenbericht w = berichte.get(ws);
            List<Berichtsheft> tage = eintraege.getOrDefault(ws, List.of());
            BerichtStatusEnum status = w == null ? BerichtStatusEnum.ENTWURF : w.getStatus();
            return new WochenberichtSummaryDTO(
                    w == null ? null : w.getId(), person.getId(), person.getFullName(), ws,
                    kalenderwoche(ws), wochenJahr(ws), status, status.getLabel(),
                    tage.stream().mapToDouble(Berichtsheft::getHours).sum(), tage.size(),
                    w == null ? null : w.getEingereichtAm(), w == null ? null : w.getGeprueftAm());
        }).toList();
    }

    // ------------------------------------------------------------------ Azubi: erfassen

    /** Legt den Tageseintrag des angemeldeten Azubis an oder aktualisiert ihn. */
    @Transactional
    public BerichtsheftEintragDTO saveEintrag(BerichtsheftSaveRequestDTO request) {
        Person person = requireLearner();
        validateEintrag(person, request);
        requireEditable(person, request.date());

        Berichtsheft eintrag = berichtsheftDAO.findByPersonAndDate(person, request.date())
                .orElseGet(() -> {
                    Berichtsheft neu = new Berichtsheft();
                    neu.setPerson(person);
                    neu.setDate(request.date());
                    return neu;
                });

        List<Lernziel> lernziele = loadLernziele(person, request.lernzielIds());
        eintrag.setHours(request.hours());
        eintrag.setArt(request.art());
        eintrag.setDescription(request.description() == null || request.description().isBlank() ? null : request.description().trim());
        eintrag.setQualifications(lernziele);
        eintrag.setUpdatedAt(LocalDateTime.now().truncatedTo(ChronoUnit.SECONDS));

        Berichtsheft saved = berichtsheftDAO.save(eintrag);
        personLernzielService.markInProgress(person, lernziele);
        return BerichtsheftEintragDTO.from(saved);
    }

    /**
     * Fachliche Regeln fuer einen Tageseintrag:
     * kein Datum in der Zukunft oder vor Ausbildungsbeginn, hoechstens 10 Stunden (ArbZG),
     * Betriebs- und Schultage brauchen Stunden und eine Beschreibung,
     * an Berufsschul-, Urlaubs-, Krankheits- und Feiertagen werden keine Lernziele angegeben
     * (Lernziele des Ausbildungsrahmenplans werden im Betrieb vermittelt).
     */
    void validateEintrag(Person person, BerichtsheftSaveRequestDTO request) {
        LocalDate date = request.date();
        if (date.isAfter(LocalDate.now())) {
            throw new InvalidRequestException("Für zukünftige Tage kann noch nichts eingetragen werden");
        }
        if (person.getDateOfTrainingStart() != null && date.isBefore(person.getDateOfTrainingStart())) {
            throw new InvalidRequestException("Der Tag liegt vor deinem Ausbildungsbeginn ("
                    + person.getDateOfTrainingStart().format(DE_DATE) + ")");
        }
        if (request.hours() > MAX_HOURS) {
            throw new InvalidRequestException("Pro Tag sind höchstens " + (int) MAX_HOURS + " Stunden erlaubt (Arbeitszeitgesetz)");
        }
        boolean anwesend = request.art() == EintragArtEnum.BETRIEB || request.art() == EintragArtEnum.SCHULE;
        if (anwesend && request.hours() <= 0) {
            throw new InvalidRequestException("Bitte gib die Stunden für " + request.art().getLabel() + " an");
        }
        if (anwesend && (request.description() == null || request.description().isBlank())) {
            throw new InvalidRequestException("Bitte beschreibe deine Tätigkeiten bzw. die Unterrichtsthemen");
        }
        boolean abwesend = request.art() == EintragArtEnum.URLAUB || request.art() == EintragArtEnum.KRANK
                || request.art() == EintragArtEnum.FEIERTAG;
        boolean hatLernziele = request.lernzielIds() != null && !request.lernzielIds().isEmpty();
        if (request.art() == EintragArtEnum.SCHULE && hatLernziele) {
            throw new InvalidRequestException("An Berufsschultagen werden keine Lernziele angegeben");
        }
        if (abwesend && hatLernziele) {
            throw new InvalidRequestException("An Urlaubs-, Krankheits- und Feiertagen werden keine Lernziele angegeben");
        }
    }

    @Transactional
    public void deleteEintrag(Long id) {
        Person person = requireLearner();
        Berichtsheft eintrag = berichtsheftDAO.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Eintrag " + id + " nicht gefunden"));
        if (!Objects.equals(eintrag.getPerson().getId(), person.getId())) {
            throw new ActionNotAllowedException("Du kannst nur deine eigenen Einträge löschen");
        }
        requireEditable(person, eintrag.getDate());
        person.getBerichtshefte().remove(eintrag);
        berichtsheftDAO.delete(eintrag);
    }

    /**
     * Reicht die Woche, in der {@code datum} liegt, zur Pruefung ein.
     * Moeglich ab dem Freitag der Woche und nur, wenn Montag bis Freitag jeweils einen Eintrag haben
     * (Urlaub, Krankheit und Feiertage werden als solche eingetragen).
     */
    @Transactional
    public WochenberichtResponseDTO einreichen(LocalDate datum) {
        Person person = requireLearner();
        LocalDate ws = weekStart(datum);
        requireEditable(person, ws);

        LocalDate freitag = ws.plusDays(4);
        if (LocalDate.now().isBefore(freitag)) {
            throw new InvalidRequestException("Die Woche kann erst ab Freitag, " + freitag.format(DE_DATE) + ", eingereicht werden");
        }

        Set<LocalDate> erfasst = berichtsheftDAO.findByPersonAndDateBetweenOrderByDateAsc(person, ws, ws.plusDays(6)).stream()
                .map(Berichtsheft::getDate)
                .collect(Collectors.toSet());
        List<String> fehlend = new ArrayList<>();
        for (int i = 0; i < 5; i++) {
            LocalDate d = ws.plusDays(i);
            boolean vorBeginn = person.getDateOfTrainingStart() != null && d.isBefore(person.getDateOfTrainingStart());
            if (!vorBeginn && !erfasst.contains(d)) {
                fehlend.add(d.getDayOfWeek().getDisplayName(TextStyle.FULL, Locale.GERMAN));
            }
        }
        if (!fehlend.isEmpty()) {
            throw new InvalidRequestException("Es fehlen noch Einträge für: " + String.join(", ", fehlend)
                    + ". Trage Urlaub, Krankheit oder Feiertage bitte als solche ein.");
        }

        Wochenbericht bericht = wochenberichtDAO.findByPersonAndWeekStart(person, ws)
                .orElseGet(() -> new Wochenbericht(person, ws));
        bericht.setStatus(BerichtStatusEnum.EINGEREICHT);
        bericht.setEingereichtAm(LocalDateTime.now());
        wochenberichtDAO.save(bericht);
        return buildWoche(person, ws);
    }

    // ------------------------------------------------------------------ Ausbilder: pruefen

    @Transactional
    public WochenberichtResponseDTO abzeichnen(Long wochenberichtId, String kommentar) {
        Wochenbericht bericht = findEingereicht(wochenberichtId);
        bericht.setStatus(BerichtStatusEnum.ABGEZEICHNET);
        bericht.setKommentar(kommentar == null || kommentar.isBlank() ? null : kommentar.trim());
        bericht.setGeprueftVon(currentUserService.getPerson().getFullName());
        bericht.setGeprueftAm(LocalDateTime.now());
        return buildWoche(bericht.getPerson(), bericht.getWeekStart());
    }

    @Transactional
    public WochenberichtResponseDTO ablehnen(Long wochenberichtId, String kommentar) {
        if (kommentar == null || kommentar.isBlank()) {
            throw new InvalidRequestException("Bitte gib einen Kommentar an, was ueberarbeitet werden soll");
        }
        Wochenbericht bericht = findEingereicht(wochenberichtId);
        bericht.setStatus(BerichtStatusEnum.ABGELEHNT);
        bericht.setKommentar(kommentar.trim());
        bericht.setGeprueftVon(currentUserService.getPerson().getFullName());
        bericht.setGeprueftAm(LocalDateTime.now());
        return buildWoche(bericht.getPerson(), bericht.getWeekStart());
    }

    // ------------------------------------------------------------------ Hilfsmethoden

    private WochenberichtResponseDTO buildWoche(Person person, LocalDate ws) {
        LocalDate we = ws.plusDays(6);
        Map<LocalDate, Berichtsheft> eintraege = berichtsheftDAO.findByPersonAndDateBetweenOrderByDateAsc(person, ws, we)
                .stream().collect(Collectors.toMap(Berichtsheft::getDate, b -> b));
        Optional<Wochenbericht> bericht = wochenberichtDAO.findByPersonAndWeekStart(person, ws);
        BerichtStatusEnum status = bericht.map(Wochenbericht::getStatus).orElse(BerichtStatusEnum.ENTWURF);

        List<WochenTagDTO> tage = new ArrayList<>();
        for (int i = 0; i < 7; i++) {
            LocalDate d = ws.plusDays(i);
            Berichtsheft b = eintraege.get(d);
            tage.add(new WochenTagDTO(d, d.getDayOfWeek().getDisplayName(TextStyle.FULL, Locale.GERMAN),
                    b == null ? null : BerichtsheftEintragDTO.from(b)));
        }

        boolean editierbar = status.isEditable() && Objects.equals(person.getId(), currentUserService.getPersonId());
        return new WochenberichtResponseDTO(
                bericht.map(Wochenbericht::getId).orElse(null),
                person.getId(), person.getFullName(), person.getBeruf(), person.getYearOfTraining(),
                ws, we, kalenderwoche(ws), wochenJahr(ws),
                status, status.getLabel(), editierbar,
                bericht.map(Wochenbericht::getKommentar).orElse(null),
                bericht.map(Wochenbericht::getGeprueftVon).orElse(null),
                bericht.map(Wochenbericht::getEingereichtAm).orElse(null),
                bericht.map(Wochenbericht::getGeprueftAm).orElse(null),
                eintraege.values().stream().mapToDouble(Berichtsheft::getHours).sum(),
                tage);
    }

    private WochenberichtSummaryDTO summary(Wochenbericht w) {
        LocalDate ws = w.getWeekStart();
        List<Berichtsheft> tage = berichtsheftDAO.findByPersonAndDateBetweenOrderByDateAsc(w.getPerson(), ws, ws.plusDays(6));
        return new WochenberichtSummaryDTO(w.getId(), w.getPerson().getId(), w.getPerson().getFullName(), ws,
                kalenderwoche(ws), wochenJahr(ws), w.getStatus(), w.getStatus().getLabel(),
                tage.stream().mapToDouble(Berichtsheft::getHours).sum(), tage.size(),
                w.getEingereichtAm(), w.getGeprueftAm());
    }

    private Person requireLearner() {
        Person person = currentUserService.getPerson();
        if (!person.getRole().isLearner()) {
            throw new ActionNotAllowedException("Nur Auszubildende und duale Studierende führen ein Berichtsheft");
        }
        return person;
    }

    private void requireEditable(Person person, LocalDate date) {
        wochenberichtDAO.findByPersonAndWeekStart(person, weekStart(date))
                .filter(w -> !w.getStatus().isEditable())
                .ifPresent(w -> {
                    throw new InvalidRequestException("Die Woche ist bereits " + w.getStatus().getLabel().toLowerCase(Locale.GERMAN)
                            + " und kann nicht mehr bearbeitet werden");
                });
    }

    private Wochenbericht findEingereicht(Long id) {
        Wochenbericht bericht = wochenberichtDAO.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Wochenbericht " + id + " nicht gefunden"));
        if (bericht.getStatus() != BerichtStatusEnum.EINGEREICHT) {
            throw new InvalidRequestException("Nur eingereichte Wochen können geprüft werden (aktuell: "
                    + bericht.getStatus().getLabel() + ")");
        }
        return bericht;
    }

    /** Laedt die angegebenen Lernziele; erlaubt sind nur Feinlernziele, die der Person zugeordnet sind. */
    private List<Lernziel> loadLernziele(Person person, List<Long> ids) {
        if (ids == null || ids.isEmpty()) {
            return List.of();
        }
        Set<Long> unique = new LinkedHashSet<>(ids);
        List<Lernziel> found = lernzielDAO.findAllById(unique);
        if (found.size() != unique.size()) {
            throw new InvalidRequestException("Mindestens ein angegebenes Lernziel existiert nicht");
        }
        for (Lernziel l : found) {
            if (l.getKind() != KindEnum.KIND_SPECIFIC) {
                throw new InvalidRequestException("Tätigkeiten werden Feinlernzielen zugeordnet – '" + l.getTitle()
                        + "' ist ein " + l.getKind().getLabel());
            }
            if (!personLernzielDAO.existsByPersonAndLernziel(person, l)) {
                throw new InvalidRequestException("Das Lernziel '" + l.getTitle() + "' ist dir nicht zugeordnet");
            }
        }
        return found;
    }

    static LocalDate weekStart(LocalDate date) {
        return date.with(DayOfWeek.MONDAY);
    }

    static int kalenderwoche(LocalDate date) {
        return date.get(WeekFields.ISO.weekOfWeekBasedYear());
    }

    static int wochenJahr(LocalDate date) {
        return date.get(WeekFields.ISO.weekBasedYear());
    }
}
