package de.fisp.azubiprojekt.service;

import de.fisp.azubiprojekt.dao.BerichtsheftDAO;
import de.fisp.azubiprojekt.dao.LernzielDAO;
import de.fisp.azubiprojekt.dao.PersonLernzielDAO;
import de.fisp.azubiprojekt.dao.WochenberichtDAO;
import de.fisp.azubiprojekt.dto.request.LernzielRequestDTO;
import de.fisp.azubiprojekt.dto.response.CsvImportResponseDTO;
import de.fisp.azubiprojekt.dto.response.LernzielResponseDTO;
import de.fisp.azubiprojekt.entity.Berichtsheft;
import de.fisp.azubiprojekt.entity.Lernziel;
import de.fisp.azubiprojekt.exceptions.InvalidRequestException;
import de.fisp.azubiprojekt.exceptions.ResourceNotFoundException;
import de.fisp.azubiprojekt.util.BerichtStatusEnum;
import de.fisp.azubiprojekt.util.KindEnum;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.time.DayOfWeek;
import java.util.*;

/**
 * Verwaltung der Lernziele inkl. CSV-Import und -Export.
 *
 * <p>Hierarchie-Regeln (Ausbildungsrahmenplan):</p>
 * <ul>
 *     <li>Ein Richtlernziel hat kein uebergeordnetes Lernziel.</li>
 *     <li>Ein Groblernziel hat immer ein Richtlernziel als uebergeordnetes Lernziel.</li>
 *     <li>Ein Feinlernziel hat immer ein Groblernziel als uebergeordnetes Lernziel.</li>
 * </ul>
 */
@Service
public class LernzielService {

    static final String CSV_DELIMITER = ";";
    static final String CSV_HEADER = "titel;beschreibung;art;uebergeordnetes_lernziel";

    private final LernzielDAO lernzielDAO;
    private final PersonLernzielDAO personLernzielDAO;
    private final BerichtsheftDAO berichtsheftDAO;
    private final WochenberichtDAO wochenberichtDAO;
    private final PersonLernzielService personLernzielService;

    public LernzielService(LernzielDAO lernzielDAO, PersonLernzielDAO personLernzielDAO, BerichtsheftDAO berichtsheftDAO,
                           WochenberichtDAO wochenberichtDAO, PersonLernzielService personLernzielService) {
        this.lernzielDAO = lernzielDAO;
        this.personLernzielDAO = personLernzielDAO;
        this.berichtsheftDAO = berichtsheftDAO;
        this.wochenberichtDAO = wochenberichtDAO;
        this.personLernzielService = personLernzielService;
    }

    @Transactional(readOnly = true)
    public List<LernzielResponseDTO> getAll(KindEnum kind) {
        List<Lernziel> lernziele = kind == null ? lernzielDAO.findAll() : lernzielDAO.findByKind(kind);
        Map<Long, Long> childCount = childCounts();
        return lernziele.stream()
                .sorted(Comparator.comparing(Lernziel::getKind)
                        .thenComparing(Lernziel::getTitle, String.CASE_INSENSITIVE_ORDER))
                .map(l -> LernzielResponseDTO.from(l, childCount.getOrDefault(l.getId(), 0L)))
                .toList();
    }

    /** Moegliche uebergeordnete Lernziele fuer eine Art (Richtlernziel: keine). */
    @Transactional(readOnly = true)
    public List<LernzielResponseDTO> getParentOptions(KindEnum kind) {
        KindEnum parentKind = kind.requiredParentKind();
        return parentKind == null ? List.of() : getAll(parentKind);
    }

    @Transactional(readOnly = true)
    public LernzielResponseDTO getById(Long id) {
        return LernzielResponseDTO.from(find(id), lernzielDAO.countByParentId(id));
    }

    @Transactional
    public LernzielResponseDTO create(LernzielRequestDTO request) {
        String title = request.title().trim();
        validateHierarchy(request.kind(), request.parentId());
        validateUniqueTitle(null, title, request.parentId());

        Lernziel saved = lernzielDAO.save(new Lernziel(title, blankToNull(request.description()),
                request.kind(), request.parentId()));
        personLernzielService.assignToAllLearners(saved);
        if (saved.getParentId() != null) {
            // Ein neues, offenes Unterziel kann den Status des uebergeordneten Ziels aendern
            personLernzielService.recomputeForAllLearners(saved.getParentId());
        }
        return LernzielResponseDTO.from(saved, 0);
    }

    @Transactional
    public LernzielResponseDTO update(Long id, LernzielRequestDTO request) {
        Lernziel lernziel = find(id);
        String title = request.title().trim();
        long children = lernzielDAO.countByParentId(id);

        if (request.kind() != lernziel.getKind()) {
            if (children > 0) {
                throw new InvalidRequestException("Die Art kann nicht geändert werden, solange " + children
                        + " untergeordnete Lernziele zugeordnet sind");
            }
            if (lernziel.getKind() == KindEnum.KIND_SPECIFIC && !berichtsheftDAO.findByQualificationsContains(lernziel).isEmpty()) {
                throw new InvalidRequestException("Die Art kann nicht geändert werden, weil das Feinlernziel "
                        + "bereits in Berichtsheft-Einträgen verwendet wird");
            }
        }
        validateHierarchy(request.kind(), request.parentId());
        validateUniqueTitle(id, title, request.parentId());

        Long oldParent = lernziel.getParentId();
        lernziel.setTitle(title);
        lernziel.setDescription(blankToNull(request.description()));
        lernziel.setKind(request.kind());
        lernziel.setParentId(request.parentId());
        Lernziel saved = lernzielDAO.save(lernziel);

        if (!Objects.equals(oldParent, saved.getParentId())) {
            if (oldParent != null) {
                personLernzielService.recomputeForAllLearners(oldParent);
            }
            if (saved.getParentId() != null) {
                personLernzielService.recomputeForAllLearners(saved.getParentId());
            }
        }
        return LernzielResponseDTO.from(saved, children);
    }

    /**
     * Loescht ein Lernziel. Nicht erlaubt, solange untergeordnete Lernziele existieren oder das Lernziel
     * in eingereichten bzw. abgezeichneten Wochen verwendet wird (die Nachweise sollen unveraendert bleiben).
     * Aus Entwuerfen wird die Zuordnung entfernt.
     */
    @Transactional
    public void delete(Long id) {
        Lernziel lernziel = find(id);
        long children = lernzielDAO.countByParentId(id);
        if (children > 0) {
            throw new InvalidRequestException("Das Lernziel hat " + children + " untergeordnete Lernziele. "
                    + "Bitte diese zuerst löschen oder einem anderen Lernziel zuordnen.");
        }

        List<Berichtsheft> eintraege = berichtsheftDAO.findByQualificationsContains(lernziel);
        long gesperrt = eintraege.stream().filter(this::isLocked).count();
        if (gesperrt > 0) {
            throw new InvalidRequestException("Das Lernziel wird in " + gesperrt + " eingereichten oder abgezeichneten "
                    + "Berichtsheft-Einträgen verwendet und kann deshalb nicht gelöscht werden.");
        }
        for (Berichtsheft b : eintraege) {
            b.getQualifications().removeIf(l -> Objects.equals(l.getId(), id));
        }

        Long parentId = lernziel.getParentId();
        personLernzielDAO.deleteAll(personLernzielDAO.findByLernziel(lernziel));
        lernzielDAO.delete(lernziel);
        lernzielDAO.flush();
        if (parentId != null) {
            personLernzielService.recomputeForAllLearners(parentId);
        }
    }

    /** Exportiert alle Lernziele als CSV (Semikolon-getrennt, UTF-8, mit Kopfzeile), Eltern vor Kindern. */
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
     * Es gelten dieselben Hierarchie-Regeln wie beim Anlegen. Fehlerhafte Zeilen werden uebersprungen
     * und gemeldet.
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
                    .orElseThrow(() -> new IllegalArgumentException("übergeordnetes Lernziel '" + parentTitle + "' nicht gefunden"));
        }
        create(new LernzielRequestDTO(title, values.get(1).trim(), kind, parentId));
    }

    /** Prueft die Regeln Richtlernziel → Groblernziel → Feinlernziel. */
    void validateHierarchy(KindEnum kind, Long parentId) {
        KindEnum requiredParent = kind.requiredParentKind();
        if (requiredParent == null) {
            if (parentId != null) {
                throw new InvalidRequestException("Ein Richtlernziel steht ganz oben und hat kein übergeordnetes Lernziel");
            }
            return;
        }
        if (parentId == null) {
            throw new InvalidRequestException("Ein " + kind.getLabel() + " braucht ein " + requiredParent.getLabel()
                    + " als übergeordnetes Lernziel");
        }
        Lernziel parent = lernzielDAO.findById(parentId)
                .orElseThrow(() -> new InvalidRequestException("Übergeordnetes Lernziel " + parentId + " existiert nicht"));
        if (parent.getKind() != requiredParent) {
            throw new InvalidRequestException("Ein " + kind.getLabel() + " muss einem " + requiredParent.getLabel()
                    + " untergeordnet sein, '" + parent.getTitle() + "' ist ein " + parent.getKind().getLabel());
        }
    }

    private void validateUniqueTitle(Long id, String title, Long parentId) {
        long self = id == null ? -1L : id;
        boolean exists = parentId == null
                ? lernzielDAO.existsByTitleIgnoreCaseAndParentIdIsNullAndIdNot(title, self)
                : lernzielDAO.existsByTitleIgnoreCaseAndParentIdAndIdNot(title, parentId, self);
        if (exists) {
            throw new InvalidRequestException("Auf dieser Ebene gibt es bereits ein Lernziel '" + title + "'");
        }
    }

    private boolean isLocked(Berichtsheft eintrag) {
        return wochenberichtDAO.findByPersonAndWeekStart(eintrag.getPerson(), eintrag.getDate().with(DayOfWeek.MONDAY))
                .map(w -> !w.getStatus().isEditable())
                .orElse(false);
    }

    private Map<Long, Long> childCounts() {
        Map<Long, Long> counts = new HashMap<>();
        for (Lernziel l : lernzielDAO.findAll()) {
            if (l.getParentId() != null) {
                counts.merge(l.getParentId(), 1L, Long::sum);
            }
        }
        return counts;
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
