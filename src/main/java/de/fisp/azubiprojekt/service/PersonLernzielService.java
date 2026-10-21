package de.fisp.azubiprojekt.service;

import de.fisp.azubiprojekt.dao.LernzielDAO;
import de.fisp.azubiprojekt.dao.PersonDAO;
import de.fisp.azubiprojekt.dao.PersonLernzielDAO;
import de.fisp.azubiprojekt.dto.response.FortschrittResponseDTO;
import de.fisp.azubiprojekt.dto.response.PersonLernzielResponseDTO;
import de.fisp.azubiprojekt.entity.Lernziel;
import de.fisp.azubiprojekt.entity.Person;
import de.fisp.azubiprojekt.entity.PersonLernziel;
import de.fisp.azubiprojekt.exceptions.InvalidRequestException;
import de.fisp.azubiprojekt.exceptions.ResourceNotFoundException;
import de.fisp.azubiprojekt.util.RoleEnum;
import de.fisp.azubiprojekt.util.StatusEnum;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.*;

/**
 * Lernziel-Zuordnungen und Fortschritt pro Person.
 *
 * <p>Regeln:</p>
 * <ul>
 *     <li>Den Status setzt der Ausbilder nur bei Lernzielen ohne Unterziele (in der Regel Feinlernziele).</li>
 *     <li>Der Status uebergeordneter Lernziele wird berechnet: alle Unterziele erreicht → Erreicht,
 *         mindestens eines begonnen → In Bearbeitung, sonst Offen.</li>
 *     <li>Der Fortschritt in Prozent zaehlt nur Lernziele ohne Unterziele.</li>
 * </ul>
 */
@Service
public class PersonLernzielService {

    private static final List<RoleEnum> LEARNER_ROLES = List.of(RoleEnum.ROLE_TRAINEE, RoleEnum.ROLE_STUDENT);

    private final PersonLernzielDAO personLernzielDAO;
    private final LernzielDAO lernzielDAO;
    private final PersonDAO personDAO;
    private final CurrentUserService currentUserService;

    public PersonLernzielService(PersonLernzielDAO personLernzielDAO, LernzielDAO lernzielDAO, PersonDAO personDAO,
                                 CurrentUserService currentUserService) {
        this.personLernzielDAO = personLernzielDAO;
        this.lernzielDAO = lernzielDAO;
        this.personDAO = personDAO;
        this.currentUserService = currentUserService;
    }

    /** Lernziele einer Person (Azubis nur sich selbst), optional gefiltert nach Status. */
    @Transactional(readOnly = true)
    public List<PersonLernzielResponseDTO> getForPerson(Long personId, StatusEnum status) {
        Person person = currentUserService.resolveAccessiblePerson(personId);
        Map<Long, Long> children = childCounts();
        List<PersonLernziel> list = status == null
                ? personLernzielDAO.findByPerson(person)
                : personLernzielDAO.findByPersonAndStatus(person, status);
        return list.stream()
                .sorted(Comparator.comparing((PersonLernziel pl) -> pl.getLernziel().getKind())
                        .thenComparing(pl -> pl.getLernziel().getTitle(), String.CASE_INSENSITIVE_ORDER))
                .map(pl -> PersonLernzielResponseDTO.from(pl, children.getOrDefault(pl.getLernziel().getId(), 0L)))
                .toList();
    }

    @Transactional(readOnly = true)
    public FortschrittResponseDTO getFortschritt(Long personId) {
        return fortschritt(currentUserService.resolveAccessiblePerson(personId), childCounts());
    }

    /** Fortschritt aller Azubis und dualen Studierenden (fuer Ausbilder-Dashboard). */
    @Transactional(readOnly = true)
    public List<FortschrittResponseDTO> getFortschrittAlle() {
        Map<Long, Long> children = childCounts();
        return personDAO.findByRoleIn(LEARNER_ROLES).stream()
                .map(p -> fortschritt(p, children))
                .sorted(Comparator.comparing(FortschrittResponseDTO::personName))
                .toList();
    }

    /** Setzt den Status eines Lernziels ohne Unterziele und berechnet die uebergeordneten Ziele neu. */
    @Transactional
    public PersonLernzielResponseDTO setStatus(Long personLernzielId, StatusEnum status) {
        PersonLernziel pl = personLernzielDAO.findById(personLernzielId)
                .orElseThrow(() -> new ResourceNotFoundException("Lernziel-Zuordnung " + personLernzielId + " nicht gefunden"));
        long children = lernzielDAO.countByParentId(pl.getLernziel().getId());
        if (children > 0) {
            throw new InvalidRequestException("Der Status von '" + pl.getLernziel().getTitle()
                    + "' wird automatisch aus den " + children + " untergeordneten Lernzielen berechnet");
        }
        pl.setStatus(status);
        PersonLernziel saved = personLernzielDAO.save(pl);
        recomputeAncestors(saved.getPerson(), saved.getLernziel().getParentId());
        return PersonLernzielResponseDTO.from(saved, 0);
    }

    /** Weist einer (neuen) lernenden Person alle Lernziele zu, die sie noch nicht hat. */
    @Transactional
    public void assignAllLernziele(Person person) {
        Set<Long> vorhanden = new HashSet<>();
        personLernzielDAO.findByPerson(person).forEach(pl -> vorhanden.add(pl.getLernziel().getId()));
        lernzielDAO.findAll().stream()
                .filter(l -> !vorhanden.contains(l.getId()))
                .forEach(l -> personLernzielDAO.save(new PersonLernziel(person, l, StatusEnum.STATUS_UNFINISHED)));
    }

    /** Weist ein neues Lernziel allen lernenden Personen zu. */
    @Transactional
    public void assignToAllLearners(Lernziel lernziel) {
        personDAO.findByRoleIn(LEARNER_ROLES).stream()
                .filter(p -> !personLernzielDAO.existsByPersonAndLernziel(p, lernziel))
                .forEach(p -> personLernzielDAO.save(new PersonLernziel(p, lernziel, StatusEnum.STATUS_UNFINISHED)));
    }

    /**
     * Setzt offene Lernziele auf "In Bearbeitung", sobald sie in einem Berichtsheft-Eintrag
     * referenziert werden, und aktualisiert die uebergeordneten Ziele.
     */
    @Transactional
    public void markInProgress(Person person, Collection<Lernziel> lernziele) {
        for (Lernziel lernziel : lernziele) {
            PersonLernziel pl = personLernzielDAO.findByPersonAndLernziel(person, lernziel)
                    .orElseGet(() -> new PersonLernziel(person, lernziel, StatusEnum.STATUS_UNFINISHED));
            if (pl.getStatus() == StatusEnum.STATUS_UNFINISHED) {
                pl.setStatus(StatusEnum.STATUS_IN_PROGRESS);
                personLernzielDAO.save(pl);
                recomputeAncestors(person, lernziel.getParentId());
            }
        }
    }

    /** Berechnet den Status eines Lernziels (und aller darueber) fuer alle Lernenden neu. */
    @Transactional
    public void recomputeForAllLearners(Long lernzielId) {
        for (Person person : personDAO.findByRoleIn(LEARNER_ROLES)) {
            recomputeAncestors(person, lernzielId);
        }
    }

    /** Berechnet ausgehend von {@code lernzielId} den Status bis zur obersten Ebene neu. */
    public void recomputeAncestors(Person person, Long lernzielId) {
        Set<Long> visited = new HashSet<>();
        Long currentId = lernzielId;
        while (currentId != null && visited.add(currentId)) {
            Lernziel current = lernzielDAO.findById(currentId).orElse(null);
            if (current == null) {
                return;
            }
            List<Lernziel> children = lernzielDAO.findByParentId(currentId);
            if (!children.isEmpty()) {
                StatusEnum derived = derive(person, children);
                PersonLernziel pl = personLernzielDAO.findByPersonAndLernziel(person, current)
                        .orElseGet(() -> new PersonLernziel(person, current, StatusEnum.STATUS_UNFINISHED));
                if (pl.getId() == null || pl.getStatus() != derived) {
                    pl.setStatus(derived);
                    personLernzielDAO.save(pl);
                }
            }
            currentId = current.getParentId();
        }
    }

    private StatusEnum derive(Person person, List<Lernziel> children) {
        boolean allDone = true;
        boolean anyStarted = false;
        for (Lernziel child : children) {
            StatusEnum s = personLernzielDAO.findByPersonAndLernziel(person, child)
                    .map(PersonLernziel::getStatus)
                    .orElse(StatusEnum.STATUS_UNFINISHED);
            allDone &= s == StatusEnum.STATUS_DONE;
            anyStarted |= s != StatusEnum.STATUS_UNFINISHED;
        }
        return allDone ? StatusEnum.STATUS_DONE : anyStarted ? StatusEnum.STATUS_IN_PROGRESS : StatusEnum.STATUS_UNFINISHED;
    }

    private FortschrittResponseDTO fortschritt(Person person, Map<Long, Long> childCounts) {
        List<PersonLernziel> blaetter = personLernzielDAO.findByPerson(person).stream()
                .filter(pl -> childCounts.getOrDefault(pl.getLernziel().getId(), 0L) == 0)
                .toList();
        long gesamt = blaetter.size();
        long erreicht = blaetter.stream().filter(pl -> pl.getStatus() == StatusEnum.STATUS_DONE).count();
        long inBearbeitung = blaetter.stream().filter(pl -> pl.getStatus() == StatusEnum.STATUS_IN_PROGRESS).count();
        long offen = gesamt - erreicht - inBearbeitung;
        int prozent = gesamt == 0 ? 0 : (int) Math.round(erreicht * 100.0 / gesamt);
        return new FortschrittResponseDTO(person.getId(), person.getFullName(), gesamt, offen, inBearbeitung, erreicht, prozent);
    }

    private Map<Long, Long> childCounts() {
        Map<Long, Long> counts = new HashMap<>();
        for (Lernziel l : lernzielDAO.findAll()) {
            if (l.getParentId() != null) {
                counts.merge(l.getParentId(), 1L, Long::sum);
            }
        }
        return counts;
    }
}
