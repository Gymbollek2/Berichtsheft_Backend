package de.fisp.azubiprojekt.controller;

import de.fisp.azubiprojekt.dto.request.PersonLernzielStatusRequestDTO;
import de.fisp.azubiprojekt.dto.response.FortschrittResponseDTO;
import de.fisp.azubiprojekt.dto.response.PersonLernzielResponseDTO;
import de.fisp.azubiprojekt.service.PersonLernzielService;
import de.fisp.azubiprojekt.util.StatusEnum;
import jakarta.validation.Valid;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/** Lernziele je Person und deren Bearbeitungsstand. */
@RestController
@RequestMapping("/v1/personLernziel")
public class PersonLernzielController {

    private final PersonLernzielService personLernzielService;

    public PersonLernzielController(PersonLernzielService personLernzielService) {
        this.personLernzielService = personLernzielService;
    }

    /** Eigene Lernziele. */
    @GetMapping("/me")
    public List<PersonLernzielResponseDTO> mine(@RequestParam(required = false) StatusEnum status) {
        return personLernzielService.getForPerson(null, status);
    }

    @GetMapping("/me/fortschritt")
    public FortschrittResponseDTO myProgress() {
        return personLernzielService.getFortschritt(null);
    }

    /** Lernziele einer bestimmten Person (Azubis nur sich selbst). */
    @GetMapping("/person/{personId}")
    public List<PersonLernzielResponseDTO> forPerson(@PathVariable Long personId,
                                                     @RequestParam(required = false) StatusEnum status) {
        return personLernzielService.getForPerson(personId, status);
    }

    @GetMapping("/person/{personId}/fortschritt")
    public FortschrittResponseDTO progress(@PathVariable Long personId) {
        return personLernzielService.getFortschritt(personId);
    }

    /** Fortschritt aller Azubis. */
    @GetMapping("/fortschritt")
    @PreAuthorize("hasRole('TRAINER')")
    public List<FortschrittResponseDTO> progressAll() {
        return personLernzielService.getFortschrittAlle();
    }

    @PutMapping("/{id}/status")
    @PreAuthorize("hasRole('TRAINER')")
    public PersonLernzielResponseDTO setStatus(@PathVariable Long id, @Valid @RequestBody PersonLernzielStatusRequestDTO request) {
        return personLernzielService.setStatus(id, request.status());
    }
}
