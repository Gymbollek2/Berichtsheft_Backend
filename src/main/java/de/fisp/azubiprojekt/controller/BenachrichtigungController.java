package de.fisp.azubiprojekt.controller;

import de.fisp.azubiprojekt.dto.response.BenachrichtigungDTO;
import de.fisp.azubiprojekt.dto.response.BenachrichtigungenResponseDTO;
import de.fisp.azubiprojekt.dto.response.MessageResponseDTO;
import de.fisp.azubiprojekt.service.BenachrichtigungService;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

/** Benachrichtigungen des angemeldeten Benutzers (Glocke oben rechts). */
@RestController
@RequestMapping("/v1/benachrichtigung")
public class BenachrichtigungController {

    private final BenachrichtigungService benachrichtigungService;

    public BenachrichtigungController(BenachrichtigungService benachrichtigungService) {
        this.benachrichtigungService = benachrichtigungService;
    }

    /** Neueste Nachrichten und Anzahl der ungelesenen. */
    @GetMapping
    public BenachrichtigungenResponseDTO eigene() {
        return benachrichtigungService.getEigene();
    }

    /** Nur die Anzahl der ungelesenen – fuer die regelmaessige Abfrage im Hintergrund. */
    @GetMapping("/anzahl")
    public Map<String, Long> anzahl() {
        return Map.of("ungelesen", benachrichtigungService.getAnzahlUngelesen());
    }

    @PostMapping("/{id}/gelesen")
    public BenachrichtigungDTO gelesen(@PathVariable Long id) {
        return benachrichtigungService.markiereGelesen(id);
    }

    @PostMapping("/gelesen")
    public MessageResponseDTO alleGelesen() {
        benachrichtigungService.markiereAlleGelesen();
        return new MessageResponseDTO("Alle Benachrichtigungen gelesen");
    }
}
