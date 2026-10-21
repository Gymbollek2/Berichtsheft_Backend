package de.fisp.azubiprojekt.controller;

import de.fisp.azubiprojekt.dto.request.BerichtsheftSaveRequestDTO;
import de.fisp.azubiprojekt.dto.request.WochenEinreichenRequestDTO;
import de.fisp.azubiprojekt.dto.request.WochenPruefungRequestDTO;
import de.fisp.azubiprojekt.dto.response.BerichtsheftEintragDTO;
import de.fisp.azubiprojekt.dto.response.MessageResponseDTO;
import de.fisp.azubiprojekt.dto.response.WochenberichtResponseDTO;
import de.fisp.azubiprojekt.dto.response.WochenberichtSummaryDTO;
import de.fisp.azubiprojekt.service.BerichtsheftService;
import jakarta.validation.Valid;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDate;
import java.util.List;

/** Berichtsheft: Tageseintraege, Wochen einreichen und abzeichnen. */
@RestController
@RequestMapping("/v1/berichtsheft")
public class BerichtsheftController {

    private final BerichtsheftService berichtsheftService;

    public BerichtsheftController(BerichtsheftService berichtsheftService) {
        this.berichtsheftService = berichtsheftService;
    }

    /** Woche ansehen. Ohne personId die eigene, ohne datum die aktuelle Woche. */
    @GetMapping("/woche")
    public WochenberichtResponseDTO woche(@RequestParam(required = false) Long personId,
                                          @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate datum) {
        return berichtsheftService.getWoche(personId, datum);
    }

    /** Uebersicht aller Wochen einer Person (ohne personId: eigene). */
    @GetMapping("/historie")
    public List<WochenberichtSummaryDTO> historie(@RequestParam(required = false) Long personId) {
        return berichtsheftService.getHistorie(personId);
    }

    @PutMapping("/eintrag")
    public BerichtsheftEintragDTO saveEintrag(@Valid @RequestBody BerichtsheftSaveRequestDTO request) {
        return berichtsheftService.saveEintrag(request);
    }

    @DeleteMapping("/eintrag/{id}")
    public MessageResponseDTO deleteEintrag(@PathVariable Long id) {
        berichtsheftService.deleteEintrag(id);
        return new MessageResponseDTO("Eintrag gelöscht");
    }

    @PostMapping("/woche/einreichen")
    public WochenberichtResponseDTO einreichen(@Valid @RequestBody WochenEinreichenRequestDTO request) {
        return berichtsheftService.einreichen(request.datum());
    }

    /** Warteschlange der eingereichten Wochen. */
    @GetMapping("/offen")
    @PreAuthorize("hasAnyRole('TRAINER','ADMIN')")
    public List<WochenberichtSummaryDTO> offen() {
        return berichtsheftService.getOffen();
    }

    @PostMapping("/woche/{id}/abzeichnen")
    @PreAuthorize("hasAnyRole('TRAINER','ADMIN')")
    public WochenberichtResponseDTO abzeichnen(@PathVariable Long id, @Valid @RequestBody(required = false) WochenPruefungRequestDTO request) {
        return berichtsheftService.abzeichnen(id, request == null ? null : request.kommentar());
    }

    @PostMapping("/woche/{id}/ablehnen")
    @PreAuthorize("hasAnyRole('TRAINER','ADMIN')")
    public WochenberichtResponseDTO ablehnen(@PathVariable Long id, @Valid @RequestBody WochenPruefungRequestDTO request) {
        return berichtsheftService.ablehnen(id, request.kommentar());
    }
}
