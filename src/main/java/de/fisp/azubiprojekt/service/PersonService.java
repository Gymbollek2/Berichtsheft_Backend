package de.fisp.azubiprojekt.service;

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

    public PersonService(PersonDAO personDAO, UserDAO userDAO, PasswordEncoder passwordEncoder,
                         CurrentUserService currentUserService, PersonLernzielService personLernzielService) {
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

        Person person = new Person(request.firstName().trim(), request.lastName().trim(), request.role(),
                request.yearOfTraining(), request.dateOfTrainingStart(), request.dateOfBirth());
        person.setBeruf(blankToNull(request.beruf()));

        String username = request.username() == null || request.username().isBlank()
                ? generateUsername(person.getFirstName(), person.getLastName())
                : request.username().trim();
        if (userDAO.existsByUsernameIgnoreCase(username)) {
            throw new InvalidRequestException("Benutzername '" + username + "' ist bereits vergeben");
        }

        Users users = new Users(username, passwordEncoder.encode(request.password()), person);
        person.setUsers(users);
        Person saved = personDAO.save(person);

        if (saved.getRole().isLearner()) {
            personLernzielService.assignAllLernziele(saved);
        }
        return PersonResponseDTO.from(saved);
    }

    @Transactional
    public PersonResponseDTO update(Long id, PersonUpdateRequestDTO request) {
        Person person = findPerson(id);
        currentUserService.requireCanManage(person.getRole());
        currentUserService.requireCanManage(request.role());
        if (Objects.equals(id, currentUserService.getPersonId()) && request.role() != person.getRole()) {
            throw new ActionNotAllowedException("Du kannst deine eigene Rolle nicht aendern");
        }

        Users users = person.getUsers();
        String username = request.username().trim();
        if (!users.getUsername().equalsIgnoreCase(username) && userDAO.existsByUsernameIgnoreCase(username)) {
            throw new InvalidRequestException("Benutzername '" + username + "' ist bereits vergeben");
        }
        if (person.getRole() != request.role()) {
            // Rollenwechsel invalidiert bestehende Tokens (Rolle steht im Token)
            users.incrementTokenVersion();
        }

        person.setFirstName(request.firstName().trim());
        person.setLastName(request.lastName().trim());
        person.setRole(request.role());
        person.setBeruf(blankToNull(request.beruf()));
        person.setYearOfTraining(request.yearOfTraining());
        person.setDateOfTrainingStart(request.dateOfTrainingStart());
        person.setDateOfBirth(request.dateOfBirth());
        users.setUsername(username);

        Person saved = personDAO.save(person);
        if (saved.getRole().isLearner()) {
            personLernzielService.assignAllLernziele(saved);
        }
        return PersonResponseDTO.from(saved);
    }

    @Transactional
    public void delete(Long id) {
        if (Objects.equals(id, currentUserService.getPersonId())) {
            throw new ActionNotAllowedException("Du kannst dich nicht selbst loeschen");
        }
        Person person = findPerson(id);
        currentUserService.requireCanManage(person.getRole());
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
