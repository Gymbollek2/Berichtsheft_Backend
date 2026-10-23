package de.fisp.azubiprojekt.controller;

import de.fisp.azubiprojekt.dto.response.BenachrichtigungDTO;
import de.fisp.azubiprojekt.dto.response.BenachrichtigungenResponseDTO;
import de.fisp.azubiprojekt.dto.response.MessageResponseDTO;
import de.fisp.azubiprojekt.service.BenachrichtigungLive;
import de.fisp.azubiprojekt.service.BenachrichtigungService;
import de.fisp.azubiprojekt.service.CurrentUserService;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.util.Map;

/** Benachrichtigungen des angemeldeten Benutzers (Glocke oben rechts). */
@RestController
@RequestMapping("/v1/benachrichtigung")
public class BenachrichtigungController {

    private final BenachrichtigungService benachrichtigungService;
    private final BenachrichtigungLive live;
    private final CurrentUserService currentUserService;

    public BenachrichtigungController(BenachrichtigungService benachrichtigungService, BenachrichtigungLive live,
                                      CurrentUserService currentUserService) {
        this.benachrichtigungService = benachrichtigungService;
        this.live = live;
        this.currentUserService = currentUserService;
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

    /** Offene Verbindung (Server-Sent Events): meldet jede Aenderung sofort, damit die Glocke direkt aktualisiert. */
    @GetMapping(path = "/live", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public SseEmitter live() {
        return live.verbinden(currentUserService.getPersonId());
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
