package de.fisp.azubiprojekt.controller;

import de.fisp.azubiprojekt.dto.request.LernzielRequestDTO;
import de.fisp.azubiprojekt.dto.response.CsvImportResponseDTO;
import de.fisp.azubiprojekt.dto.response.LernzielResponseDTO;
import de.fisp.azubiprojekt.dto.response.MessageResponseDTO;
import de.fisp.azubiprojekt.exceptions.InvalidRequestException;
import de.fisp.azubiprojekt.service.LernzielService;
import de.fisp.azubiprojekt.util.KindEnum;
import jakarta.validation.Valid;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.List;

/** Lernziele lesen (alle) und verwalten (Ausbilder/Admin). */
@RestController
@RequestMapping("/v1/lernziel")
public class LernzielController {

    private final LernzielService lernzielService;

    public LernzielController(LernzielService lernzielService) {
        this.lernzielService = lernzielService;
    }

    @GetMapping
    public List<LernzielResponseDTO> getAll(@RequestParam(required = false) KindEnum kind) {
        return lernzielService.getAll(kind);
    }

    @GetMapping("/{id}")
    public LernzielResponseDTO getById(@PathVariable Long id) {
        return lernzielService.getById(id);
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    @PreAuthorize("hasAnyRole('TRAINER','ADMIN')")
    public LernzielResponseDTO create(@Valid @RequestBody LernzielRequestDTO request) {
        return lernzielService.create(request);
    }

    @PutMapping("/{id}")
    @PreAuthorize("hasAnyRole('TRAINER','ADMIN')")
    public LernzielResponseDTO update(@PathVariable Long id, @Valid @RequestBody LernzielRequestDTO request) {
        return lernzielService.update(id, request);
    }

    @DeleteMapping("/{id}")
    @PreAuthorize("hasAnyRole('TRAINER','ADMIN')")
    public MessageResponseDTO delete(@PathVariable Long id) {
        lernzielService.delete(id);
        return new MessageResponseDTO("Lernziel geloescht");
    }

    @GetMapping(value = "/export", produces = "text/csv")
    @PreAuthorize("hasAnyRole('TRAINER','ADMIN')")
    public ResponseEntity<byte[]> exportCsv() {
        // BOM, damit Excel die Umlaute korrekt anzeigt
        byte[] body = ("﻿" + lernzielService.exportCsv()).getBytes(StandardCharsets.UTF_8);
        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"lernziele.csv\"")
                .contentType(new MediaType("text", "csv", StandardCharsets.UTF_8))
                .body(body);
    }

    @PostMapping(value = "/import", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @PreAuthorize("hasAnyRole('TRAINER','ADMIN')")
    public CsvImportResponseDTO importCsv(@RequestParam("file") MultipartFile file) {
        if (file.isEmpty()) {
            throw new InvalidRequestException("Die hochgeladene Datei ist leer");
        }
        try {
            return lernzielService.importCsv(file.getInputStream());
        } catch (IOException e) {
            throw new InvalidRequestException("Datei konnte nicht gelesen werden");
        }
    }
}
