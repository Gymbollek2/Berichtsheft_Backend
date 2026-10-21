package de.fisp.azubiprojekt.service;

import de.fisp.azubiprojekt.dao.LernzielDAO;
import de.fisp.azubiprojekt.dao.PersonDAO;
import de.fisp.azubiprojekt.dao.PersonLernzielDAO;
import de.fisp.azubiprojekt.dto.response.FortschrittResponseDTO;
import de.fisp.azubiprojekt.dto.response.PersonLernzielResponseDTO;
import de.fisp.azubiprojekt.entity.Lernziel;
import de.fisp.azubiprojekt.entity.Person;
import de.fisp.azubiprojekt.entity.PersonLernziel;
import de.fisp.azubiprojekt.exceptions.ResourceNotFoundException;
import de.fisp.azubiprojekt.util.RoleEnum;
import de.fisp.azubiprojekt.util.StatusEnum;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Collection;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/** Lernziel-Zuordnungen und Fortschritt pro Person. */
@Service
public class PersonLernzielService {

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
        List<PersonLernziel> list = status == null
                ? personLernzielDAO.findByPerson(person)
                : personLernzielDAO.findByPersonAndStatus(person, status);
        return list.stream()
                .sorted(Comparator.comparing((PersonLernziel pl) -> pl.getLernziel().getKind())
                        .thenComparing(pl -> pl.getLernziel().getTitle(), String.CASE_INSENSITIVE_ORDER))
                .map(PersonLernzielResponseDTO::from)
                .toList();
    }

    @Transactional(readOnly = true)
    public FortschrittResponseDTO getFortschritt(Long personId) {
        return fortschritt(currentUserService.resolveAccessiblePerson(personId));
    }

    /** Fortschritt aller Azubis und dualen Studierenden (fuer Ausbilder-Dashboard). */
    @Transactional(readOnly = true)
    public List<FortschrittResponseDTO> getFortschrittAlle() {
        return personDAO.findByRoleIn(List.of(RoleEnum.ROLE_TRAINEE, RoleEnum.ROLE_STUDENT)).stream()
                .map(this::fortschritt)
                .sorted(Comparator.comparing(FortschrittResponseDTO::personName))
                .toList();
    }

    @Transactional
    public PersonLernzielResponseDTO setStatus(Long personLernzielId, StatusEnum status) {
        PersonLernziel pl = personLernzielDAO.findById(personLernzielId)
                .orElseThrow(() -> new ResourceNotFoundException("Lernziel-Zuordnung " + personLernzielId + " nicht gefunden"));
        pl.setStatus(status);
        return PersonLernzielResponseDTO.from(personLernzielDAO.save(pl));
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
        personDAO.findByRoleIn(List.of(RoleEnum.ROLE_TRAINEE, RoleEnum.ROLE_STUDENT)).stream()
                .filter(p -> !personLernzielDAO.existsByPersonAndLernziel(p, lernziel))
                .forEach(p -> personLernzielDAO.save(new PersonLernziel(p, lernziel, StatusEnum.STATUS_UNFINISHED)));
    }

    /**
     * Setzt offene Lernziele auf "In Bearbeitung", sobald sie in einem Berichtsheft-Eintrag
     * referenziert werden. Erreichte Lernziele bleiben unveraendert.
     */
    @Transactional
    public void markInProgress(Person person, Collection<Lernziel> lernziele) {
        for (Lernziel lernziel : lernziele) {
            PersonLernziel pl = personLernzielDAO.findByPersonAndLernziel(person, lernziel)
                    .orElseGet(() -> new PersonLernziel(person, lernziel, StatusEnum.STATUS_UNFINISHED));
            if (pl.getStatus() == StatusEnum.STATUS_UNFINISHED) {
                pl.setStatus(StatusEnum.STATUS_IN_PROGRESS);
                personLernzielDAO.save(pl);
            }
        }
    }

    private FortschrittResponseDTO fortschritt(Person person) {
        List<PersonLernziel> list = personLernzielDAO.findByPerson(person);
        long gesamt = list.size();
        long erreicht = list.stream().filter(pl -> pl.getStatus() == StatusEnum.STATUS_DONE).count();
        long inBearbeitung = list.stream().filter(pl -> pl.getStatus() == StatusEnum.STATUS_IN_PROGRESS).count();
        long offen = gesamt - erreicht - inBearbeitung;
        int prozent = gesamt == 0 ? 0 : (int) Math.round(erreicht * 100.0 / gesamt);
        return new FortschrittResponseDTO(person.getId(), person.getFullName(), gesamt, offen, inBearbeitung, erreicht, prozent);
    }
}
