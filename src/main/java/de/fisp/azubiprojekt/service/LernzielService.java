package de.fisp.azubiprojekt.service;

import de.fisp.azubiprojekt.dao.BerichtsheftDAO;
import de.fisp.azubiprojekt.dao.LernzielDAO;
import de.fisp.azubiprojekt.dao.PersonLernzielDAO;
import de.fisp.azubiprojekt.dto.request.LernzielRequestDTO;
import de.fisp.azubiprojekt.dto.response.CsvImportResponseDTO;
import de.fisp.azubiprojekt.dto.response.LernzielResponseDTO;
import de.fisp.azubiprojekt.entity.Berichtsheft;
import de.fisp.azubiprojekt.entity.Lernziel;
import de.fisp.azubiprojekt.exceptions.InvalidRequestException;
import de.fisp.azubiprojekt.exceptions.ResourceNotFoundException;
import de.fisp.azubiprojekt.util.KindEnum;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.*;

/** Verwaltung der Lernziele inkl. CSV-Import und -Export. */
@Service
public class LernzielService {

    static final String CSV_DELIMITER = ";";
    static final String CSV_HEADER = "titel;beschreibung;art;uebergeordnetes_lernziel";

    private final LernzielDAO lernzielDAO;
    private final PersonLernzielDAO personLernzielDAO;
    private final BerichtsheftDAO berichtsheftDAO;
    private final PersonLernzielService personLernzielService;

    public LernzielService(LernzielDAO lernzielDAO, PersonLernzielDAO personLernzielDAO, BerichtsheftDAO berichtsheftDAO,
                           PersonLernzielService personLernzielService) {
        this.lernzielDAO = lernzielDAO;
        this.personLernzielDAO = personLernzielDAO;
        this.berichtsheftDAO = berichtsheftDAO;
        this.personLernzielService = personLernzielService;
    }

    @Transactional(readOnly = true)
    public List<LernzielResponseDTO> getAll(KindEnum kind) {
        List<Lernziel> lernziele = kind == null ? lernzielDAO.findAll() : lernzielDAO.findByKind(kind);
        return lernziele.stream()
                .sorted(Comparator.comparing(Lernziel::getKind)
                        .thenComparing(Lernziel::getTitle, String.CASE_INSENSITIVE_ORDER))
                .map(LernzielResponseDTO::from)
                .toList();
    }

    @Transactional(readOnly = true)
    public LernzielResponseDTO getById(Long id) {
        return LernzielResponseDTO.from(find(id));
    }

    @Transactional
    public LernzielResponseDTO create(LernzielRequestDTO request) {
        validateParent(null, request.parentId());
        Lernziel saved = lernzielDAO.save(new Lernziel(request.title().trim(), blankToNull(request.description()),
                request.kind(), request.parentId()));
        personLernzielService.assignToAllLearners(saved);
        return LernzielResponseDTO.from(saved);
    }

    @Transactional
    public LernzielResponseDTO update(Long id, LernzielRequestDTO request) {
        Lernziel lernziel = find(id);
        validateParent(id, request.parentId());
        lernziel.setTitle(request.title().trim());
        lernziel.setDescription(blankToNull(request.description()));
        lernziel.setKind(request.kind());
        lernziel.setParentId(request.parentId());
        return LernzielResponseDTO.from(lernzielDAO.save(lernziel));
    }

    /**
     * Loescht ein Lernziel samt aller Zuordnungen. Untergeordnete Lernziele bleiben erhalten
     * und werden eine Ebene nach oben gehaengt.
     */
    @Transactional
    public void delete(Long id) {
        Lernziel lernziel = find(id);
        personLernzielDAO.deleteAll(personLernzielDAO.findByLernziel(lernziel));
        for (Berichtsheft b : berichtsheftDAO.findByQualificationsContains(lernziel)) {
            b.getQualifications().removeIf(l -> Objects.equals(l.getId(), id));
        }
        for (Lernziel child : lernzielDAO.findByParentId(id)) {
            child.setParentId(lernziel.getParentId());
        }
        lernzielDAO.delete(lernziel);
    }

    /** Exportiert alle Lernziele als CSV (Semikolon-getrennt, UTF-8, mit Kopfzeile). */
    @Transactional(readOnly = true)
    public String exportCsv() {
        Map<Long, String> titles = new HashMap<>();
        List<Lernziel> all = lernzielDAO.findAll();
        all.forEach(l -> titles.put(l.getId(), l.getTitle()));

        StringBuilder sb = new StringBuilder(CSV_HEADER).append('\n');
        all.stream()
                .sorted(Comparator.comparing(Lernziel::getKind).thenComparing(Lernziel::getTitle))
                .forEach(l -> sb.append(csv(l.getTitle())).append(CSV_DELIMITER)
                        .append(csv(l.getDescription())).append(CSV_DELIMITER)
                        .append(l.getKind().getLabel()).append(CSV_DELIMITER)
                        .append(csv(l.getParentId() == null ? null : titles.get(l.getParentId())))
                        .append('\n'));
        return sb.toString();
    }

    /**
     * Importiert Lernziele aus einer CSV-Datei: {@code titel;beschreibung;art;uebergeordnetes_lernziel}.
     * Die Art kann als Enum-Name oder deutsche Bezeichnung angegeben werden (z. B. "Groblernziel").
     * Das uebergeordnete Lernziel wird ueber den Titel gesucht (muss vorher in der Datei oder DB stehen).
     */
    @Transactional
    public CsvImportResponseDTO importCsv(InputStream input) {
        List<String> fehler = new ArrayList<>();
        int importiert = 0;
        try (BufferedReader reader = new BufferedReader(new InputStreamReader(input, StandardCharsets.UTF_8))) {
            String line;
            int lineNo = 0;
            while ((line = reader.readLine()) != null) {
                lineNo++;
                if (lineNo == 1 && line.startsWith("﻿")) {
                    line = line.substring(1); // BOM aus Excel entfernen
                }
                if (line.isBlank() || (lineNo == 1 && line.toLowerCase(Locale.ROOT).startsWith("titel"))) {
                    continue;
                }
                try {
                    importLine(line);
                    importiert++;
                } catch (RuntimeException e) {
                    fehler.add("Zeile " + lineNo + ": " + e.getMessage());
                }
            }
        } catch (IOException e) {
            throw new InvalidRequestException("Datei konnte nicht gelesen werden: " + e.getMessage());
        }
        return new CsvImportResponseDTO(importiert, fehler);
    }

    private void importLine(String line) {
        List<String> values = splitCsvLine(line);
        if (values.size() < 3) {
            throw new IllegalArgumentException("mindestens 3 Spalten erwartet (titel;beschreibung;art)");
        }
        String title = values.get(0).trim();
        if (title.isBlank()) {
            throw new IllegalArgumentException("Titel fehlt");
        }
        KindEnum kind = KindEnum.parse(values.get(2).trim());
        Long parentId = null;
        if (values.size() > 3 && !values.get(3).isBlank()) {
            String parentTitle = values.get(3).trim();
            parentId = lernzielDAO.findFirstByTitleIgnoreCase(parentTitle)
                    .map(Lernziel::getId)
                    .orElseThrow(() -> new IllegalArgumentException("uebergeordnetes Lernziel '" + parentTitle + "' nicht gefunden"));
        }
        create(new LernzielRequestDTO(title, values.get(1).trim(), kind, parentId));
    }

    private void validateParent(Long id, Long parentId) {
        if (parentId == null) {
            return;
        }
        if (Objects.equals(id, parentId)) {
            throw new InvalidRequestException("Ein Lernziel kann nicht sein eigenes uebergeordnetes Lernziel sein");
        }
        Lernziel current = lernzielDAO.findById(parentId)
                .orElseThrow(() -> new InvalidRequestException("Uebergeordnetes Lernziel " + parentId + " existiert nicht"));
        // Zyklen verhindern (A -> B -> A)
        Set<Long> visited = new HashSet<>();
        while (current != null && current.getParentId() != null && visited.add(current.getId())) {
            if (Objects.equals(current.getParentId(), id)) {
                throw new InvalidRequestException("Diese Zuordnung wuerde einen Zyklus erzeugen");
            }
            current = lernzielDAO.findById(current.getParentId()).orElse(null);
        }
    }

    private Lernziel find(Long id) {
        return lernzielDAO.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Lernziel mit Id " + id + " nicht gefunden"));
    }

    private static String csv(String value) {
        if (value == null) {
            return "";
        }
        String v = value.replace("\r", " ").replace("\n", " ");
        return v.contains(CSV_DELIMITER) || v.contains("\"") ? "\"" + v.replace("\"", "\"\"") + "\"" : v;
    }

    /** Zerlegt eine CSV-Zeile; Felder in Anfuehrungszeichen duerfen Semikolons und "" enthalten. */
    static List<String> splitCsvLine(String line) {
        List<String> fields = new ArrayList<>();
        StringBuilder current = new StringBuilder();
        boolean quoted = false;
        for (int i = 0; i < line.length(); i++) {
            char c = line.charAt(i);
            if (quoted) {
                if (c == '"' && i + 1 < line.length() && line.charAt(i + 1) == '"') {
                    current.append('"');
                    i++;
                } else if (c == '"') {
                    quoted = false;
                } else {
                    current.append(c);
                }
            } else if (c == '"') {
                quoted = true;
            } else if (c == ';') {
                fields.add(current.toString());
                current.setLength(0);
            } else {
                current.append(c);
            }
        }
        fields.add(current.toString());
        return fields;
    }

    private static String blankToNull(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }
}
