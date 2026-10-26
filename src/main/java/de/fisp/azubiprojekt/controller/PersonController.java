package de.fisp.azubiprojekt.controller;

import de.fisp.azubiprojekt.dto.request.PasswordResetRequestDTO;
import de.fisp.azubiprojekt.dto.request.PersonCreateRequestDTO;
import de.fisp.azubiprojekt.dto.request.PersonUpdateRequestDTO;
import de.fisp.azubiprojekt.dto.response.MessageResponseDTO;
import de.fisp.azubiprojekt.dto.response.OrganigrammResponseDTO;
import de.fisp.azubiprojekt.dto.response.PersonResponseDTO;
import de.fisp.azubiprojekt.service.PersonService;
import de.fisp.azubiprojekt.util.RoleEnum;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/** Personen- und Benutzerverwaltung (nur Ausbilder) und das Ausbildungsorganigramm (fuer alle). */
@RestController
@RequestMapping("/v1/person")
public class PersonController {

    private final PersonService personService;

    public PersonController(PersonService personService) {
        this.personService = personService;
    }

    @GetMapping
    @PreAuthorize("hasRole('TRAINER')")
    public List<PersonResponseDTO> getAll(@RequestParam(required = false) RoleEnum role) {
        return personService.getAll(role);
    }

    @GetMapping("/azubis")
    @PreAuthorize("hasRole('TRAINER')")
    public List<PersonResponseDTO> getLearners() {
        return personService.getLearners();
    }

    /** Ausbildungsorganigramm – fuer alle angemeldeten Personen. */
    @GetMapping("/organigramm")
    public OrganigrammResponseDTO getOrganigramm() {
        return personService.getOrganigramm();
    }

    /** Azubis duerfen nur sich selbst abrufen – wird im Service geprueft. */
    @GetMapping("/{id}")
    public PersonResponseDTO getById(@PathVariable Long id) {
        return personService.getById(id);
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    @PreAuthorize("hasRole('TRAINER')")
    public PersonResponseDTO create(@Valid @RequestBody PersonCreateRequestDTO request) {
        return personService.create(request);
    }

    @PutMapping("/{id}")
    @PreAuthorize("hasRole('TRAINER')")
    public PersonResponseDTO update(@PathVariable Long id, @Valid @RequestBody PersonUpdateRequestDTO request) {
        return personService.update(id, request);
    }

    @DeleteMapping("/{id}")
    @PreAuthorize("hasRole('TRAINER')")
    public MessageResponseDTO delete(@PathVariable Long id) {
        personService.delete(id);
        return new MessageResponseDTO("Person gelöscht");
    }

    @PostMapping("/{id}/reset-password")
    @PreAuthorize("hasRole('TRAINER')")
    public MessageResponseDTO resetPassword(@PathVariable Long id, @Valid @RequestBody PasswordResetRequestDTO request) {
        personService.resetPassword(id, request);
        return new MessageResponseDTO("Passwort zurückgesetzt");
    }
}
