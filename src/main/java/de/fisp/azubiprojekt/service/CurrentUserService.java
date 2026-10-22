package de.fisp.azubiprojekt.service;

import de.fisp.azubiprojekt.dao.PersonDAO;
import de.fisp.azubiprojekt.entity.Person;
import de.fisp.azubiprojekt.entity.UserAuthentication;
import de.fisp.azubiprojekt.exceptions.ActionNotAllowedException;
import de.fisp.azubiprojekt.exceptions.ResourceNotFoundException;
import de.fisp.azubiprojekt.util.RoleEnum;
import org.springframework.security.authentication.AuthenticationCredentialsNotFoundException;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;

import java.util.Objects;

/** Zugriff auf den aktuell angemeldeten Benutzer und zentrale Berechtigungspruefungen. */
@Service
public class CurrentUserService {

    private final PersonDAO personDAO;

    public CurrentUserService(PersonDAO personDAO) {
        this.personDAO = personDAO;
    }

    public UserAuthentication getPrincipal() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth == null || !(auth.getPrincipal() instanceof UserAuthentication user)) {
            throw new AuthenticationCredentialsNotFoundException("Nicht angemeldet");
        }
        return user;
    }

    public Long getPersonId() {
        return getPrincipal().getPersonId();
    }

    public RoleEnum getRole() {
        return getPrincipal().getRole();
    }

    public boolean isStaff() {
        return getRole().isStaff();
    }

    public Person getPerson() {
        return personDAO.findById(getPersonId())
                .orElseThrow(() -> new ResourceNotFoundException("Angemeldete Person existiert nicht mehr"));
    }

    /**
     * Ermittelt die Person, auf die zugegriffen werden soll.
     * Azubis duerfen nur ihre eigenen Daten sehen; Ausbilder jede Person.
     *
     * @param personId gewuenschte Person oder {@code null} fuer "ich selbst"
     */
    public Person resolveAccessiblePerson(Long personId) {
        if (personId == null || Objects.equals(personId, getPersonId())) {
            return getPerson();
        }
        if (!isStaff()) {
            throw new ActionNotAllowedException("Du darfst nur deine eigenen Daten einsehen");
        }
        return personDAO.findById(personId)
                .orElseThrow(() -> new ResourceNotFoundException("Person mit Id " + personId + " nicht gefunden"));
    }

    /**
     * Prueft, ob der angemeldete Benutzer eine Person mit der angegebenen Rolle verwalten darf.
     * Ausbilder duerfen alle Personen verwalten (Azubis, Studierende und andere Ausbilder).
     */
    public void requireCanManage(RoleEnum targetRole) {
        if (!isStaff()) {
            throw new ActionNotAllowedException("Nur Ausbilder dürfen Personen verwalten");
        }
    }
}
