package de.fisp.azubiprojekt.service;

import de.fisp.azubiprojekt.dao.LernzielDAO;
import de.fisp.azubiprojekt.dao.PersonDAO;
import de.fisp.azubiprojekt.dao.UserDAO;
import de.fisp.azubiprojekt.dto.request.PasswordResetRequestDTO;
import de.fisp.azubiprojekt.dto.request.PersonCreateRequestDTO;
import de.fisp.azubiprojekt.dto.request.PersonUpdateRequestDTO;
import de.fisp.azubiprojekt.dto.response.PersonResponseDTO;
import de.fisp.azubiprojekt.entity.Person;
import de.fisp.azubiprojekt.entity.Users;
import de.fisp.azubiprojekt.exceptions.ActionNotAllowedException;
import de.fisp.azubiprojekt.exceptions.InvalidRequestException;
import de.fisp.azubiprojekt.exceptions.ResourceNotFoundException;
import de.fisp.azubiprojekt.util.RoleEnum;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.text.Normalizer;
import java.time.LocalDate;
import java.util.List;
import java.util.Locale;
import java.util.Objects;

/** Verwaltung von Personen und deren Login-Konten. */
@Service
public class PersonService {

    private final PersonDAO personDAO;
    private final UserDAO userDAO;
    private final PasswordEncoder passwordEncoder;
    private final CurrentUserService currentUserService;
    private final PersonLernzielService personLernzielService;
    private final LernzielDAO lernzielDAO;

    public PersonService(PersonDAO personDAO, UserDAO userDAO, PasswordEncoder passwordEncoder,
                         CurrentUserService currentUserService, PersonLernzielService personLernzielService,
                         LernzielDAO lernzielDAO) {
        this.lernzielDAO = lernzielDAO;
        this.personDAO = personDAO;
        this.userDAO = userDAO;
        this.passwordEncoder = passwordEncoder;
        this.currentUserService = currentUserService;
        this.personLernzielService = personLernzielService;
    }

    @Transactional(readOnly = true)
    public List<PersonResponseDTO> getAll(RoleEnum role) {
        List<Person> persons = role == null
                ? personDAO.findAllByOrderByLastNameAscFirstNameAsc()
                : personDAO.findByRole(role);
        return persons.stream().map(PersonResponseDTO::from).toList();
    }

    @Transactional(readOnly = true)
    public List<PersonResponseDTO> getLearners() {
        return personDAO.findByRoleIn(List.of(RoleEnum.ROLE_TRAINEE, RoleEnum.ROLE_STUDENT)).stream()
                .map(PersonResponseDTO::from)
                .toList();
    }

    @Transactional(readOnly = true)
    public PersonResponseDTO getById(Long id) {
        return PersonResponseDTO.from(currentUserService.resolveAccessiblePerson(id));
    }

    @Transactional
    public PersonResponseDTO create(PersonCreateRequestDTO request) {
        currentUserService.requireCanManage(request.role());
        validateTrainingData(request.role(), request.dateOfTrainingStart(), request.dateOfBirth());

        Person person = new Person(request.firstName().trim(), request.lastName().trim(), request.role(),
                0, null, request.dateOfBirth());
        applyRoleSpecificData(person, request.beruf(), request.dateOfTrainingStart());

        String username = request.username() == null || request.username().isBlank()
                ? generateUsername(person.getFirstName(), person.getLastName())
                : request.username().trim().toLowerCase(Locale.ROOT);
        if (userDAO.existsByUsernameIgnoreCase(username)) {
            throw new InvalidRequestException("Benutzername '" + username + "' ist bereits vergeben");
        }

        Users users = new Users(username, passwordEncoder.encode(request.password()), person);
        person.setUsers(users);
        Person saved = personDAO.save(person);

        if (saved.getRole().isLearner()) {
            personLernzielService.assignAllLernziele(saved);
            lernzielRecompute(saved);
        }
        return PersonResponseDTO.from(saved);
    }

    @Transactional
    public PersonResponseDTO update(Long id, PersonUpdateRequestDTO request) {
        Person person = findPerson(id);
        RoleEnum oldRole = person.getRole();
        currentUserService.requireCanManage(oldRole);
        currentUserService.requireCanManage(request.role());
        if (Objects.equals(id, currentUserService.getPersonId()) && request.role() != oldRole) {
            throw new ActionNotAllowedException("Du kannst deine eigene Rolle nicht ändern");
        }
        if (oldRole == RoleEnum.ROLE_ADMIN && request.role() != RoleEnum.ROLE_ADMIN && isLastAdmin()) {
            throw new InvalidRequestException("Es muss mindestens ein Administrator erhalten bleiben");
        }
        if (oldRole.isLearner() && !request.role().isLearner() && !person.getBerichtshefte().isEmpty()) {
            throw new InvalidRequestException("Die Person hat bereits Berichtsheft-Einträge und kann deshalb "
                    + "nicht zu " + request.role().getLabel() + " gemacht werden");
        }
        validateTrainingData(request.role(), request.dateOfTrainingStart(), request.dateOfBirth());

        Users users = person.getUsers();
        String username = request.username().trim().toLowerCase(Locale.ROOT);
        if (!users.getUsername().equalsIgnoreCase(username) && userDAO.existsByUsernameIgnoreCase(username)) {
            throw new InvalidRequestException("Benutzername '" + username + "' ist bereits vergeben");
        }
        if (oldRole != request.role()) {
            // Rollenwechsel invalidiert bestehende Tokens (Rolle steht im Token)
            users.incrementTokenVersion();
        }

        person.setFirstName(request.firstName().trim());
        person.setLastName(request.lastName().trim());
        person.setRole(request.role());
        person.setDateOfBirth(request.dateOfBirth());
        applyRoleSpecificData(person, request.beruf(), request.dateOfTrainingStart());
        users.setUsername(username);

        Person saved = personDAO.save(person);
        if (saved.getRole().isLearner()) {
            personLernzielService.assignAllLernziele(saved);
            lernzielRecompute(saved);
        }
        return PersonResponseDTO.from(saved);
    }

    @Transactional
    public void delete(Long id) {
        if (Objects.equals(id, currentUserService.getPersonId())) {
            throw new ActionNotAllowedException("Du kannst dich nicht selbst löschen");
        }
        Person person = findPerson(id);
        currentUserService.requireCanManage(person.getRole());
        if (person.getRole() == RoleEnum.ROLE_ADMIN && isLastAdmin()) {
            throw new InvalidRequestException("Der letzte Administrator kann nicht gelöscht werden");
        }
        personDAO.delete(person);
    }

    @Transactional
    public void resetPassword(Long id, PasswordResetRequestDTO request) {
        Person person = findPerson(id);
        currentUserService.requireCanManage(person.getRole());
        Users users = person.getUsers();
        users.setPassword(passwordEncoder.encode(request.newPassword()));
        users.incrementTokenVersion();
        userDAO.save(users);
    }

    /**
     * Ausbildungsdaten: Lernende brauchen einen Ausbildungsbeginn (hoechstens ein Jahr in der Zukunft),
     * das Geburtsdatum muss vor dem Ausbildungsbeginn liegen.
     */
    void validateTrainingData(RoleEnum role, LocalDate trainingStart, LocalDate birth) {
        if (role.isLearner()) {
            if (trainingStart == null) {
                throw new InvalidRequestException("Für " + role.getLabel() + " ist der Ausbildungsbeginn Pflicht");
            }
            if (trainingStart.isAfter(LocalDate.now().plusYears(1))) {
                throw new InvalidRequestException("Der Ausbildungsbeginn darf höchstens ein Jahr in der Zukunft liegen");
            }
            if (birth != null && !birth.isBefore(trainingStart)) {
                throw new InvalidRequestException("Das Geburtsdatum muss vor dem Ausbildungsbeginn liegen");
            }
        }
    }

    /** Ausbildungsberuf und -beginn gibt es nur fuer Lernende; bei Ausbildern/Admins werden sie geleert. */
    private void applyRoleSpecificData(Person person, String beruf, LocalDate trainingStart) {
        if (person.getRole().isLearner()) {
            person.setBeruf(blankToNull(beruf));
            person.setDateOfTrainingStart(trainingStart);
        } else {
            person.setBeruf(null);
            person.setDateOfTrainingStart(null);
        }
        person.setYearOfTraining(0);
    }

    private void lernzielRecompute(Person person) {
        lernzielDAO.findAll().stream()
                .filter(l -> l.getParentId() == null)
                .forEach(root -> lernzielDAO.findByParentId(root.getId()).forEach(grob ->
                        personLernzielService.recomputeAncestors(person, grob.getId())));
    }

    private boolean isLastAdmin() {
        return personDAO.findByRole(RoleEnum.ROLE_ADMIN).size() <= 1;
    }

    private Person findPerson(Long id) {
        return personDAO.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Person mit Id " + id + " nicht gefunden"));
    }

    /** Erzeugt einen eindeutigen Benutzernamen nach dem Schema vorname.nachname (ohne Umlaute). */
    String generateUsername(String firstName, String lastName) {
        String base = normalize(firstName) + "." + normalize(lastName);
        String candidate = base;
        int counter = 2;
        while (userDAO.existsByUsernameIgnoreCase(candidate)) {
            candidate = base + counter++;
        }
        return candidate;
    }

    private static String normalize(String value) {
        String s = value.trim().toLowerCase(Locale.GERMAN)
                .replace("ä", "ae").replace("ö", "oe").replace("ü", "ue").replace("ß", "ss");
        s = Normalizer.normalize(s, Normalizer.Form.NFD).replaceAll("\\p{M}", "");
        return s.replaceAll("[^a-z0-9]+", "-").replaceAll("(^-|-$)", "");
    }

    private static String blankToNull(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }
}
