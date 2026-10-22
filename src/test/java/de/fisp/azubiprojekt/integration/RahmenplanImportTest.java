package de.fisp.azubiprojekt.integration;

import de.fisp.azubiprojekt.config.RahmenplanInitializer;
import de.fisp.azubiprojekt.dao.LernzielDAO;
import de.fisp.azubiprojekt.dao.PersonDAO;
import de.fisp.azubiprojekt.dao.PersonLernzielDAO;
import de.fisp.azubiprojekt.entity.Lernziel;
import de.fisp.azubiprojekt.entity.Person;
import de.fisp.azubiprojekt.util.KindEnum;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.DefaultApplicationArguments;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Prueft, dass der Ausbildungsrahmenplan (FIAE) beim Start vollstaendig und mit korrekter Hierarchie
 * eingespielt wird: 17 Richtlernziele, 86 Groblernziele, 86 Beispiel-Feinlernziele.
 */
@SpringBootTest(properties = {
        "spring.datasource.url=jdbc:h2:mem:rahmenplan-test;DB_CLOSE_DELAY=-1",
        "spring.jpa.hibernate.ddl-auto=create-drop",
        "server.ssl.enabled=false",
        "app.demo-data.enabled=true",
        "app.rahmenplan-import.enabled=true"
})
class RahmenplanImportTest {

    private static final int RICHT = 17;
    private static final int GROB = 86;
    private static final int FEIN = 86;

    @Autowired
    private LernzielDAO lernzielDAO;
    @Autowired
    private PersonDAO personDAO;
    @Autowired
    private PersonLernzielDAO personLernzielDAO;
    @Autowired
    private RahmenplanInitializer initializer;

    @Test
    @Transactional
    void rahmenplanIsImportedWithHierarchy() {
        List<Lernziel> rahmenplan = rahmenplanLernziele();
        assertEquals(RICHT, count(rahmenplan, KindEnum.KIND_GENERAL));
        assertEquals(GROB, count(rahmenplan, KindEnum.KIND_BROAD));
        assertEquals(FEIN, count(rahmenplan, KindEnum.KIND_SPECIFIC));

        Map<Long, Lernziel> byId = lernzielDAO.findAll().stream().collect(Collectors.toMap(Lernziel::getId, Function.identity()));
        for (Lernziel l : rahmenplan) {
            KindEnum required = l.getKind().requiredParentKind();
            if (required == null) {
                assertNull(l.getParentId(), l.getTitle());
            } else {
                assertEquals(required, byId.get(l.getParentId()).getKind(), l.getTitle());
            }
        }
        // jedes Groblernziel hat genau ein Beispiel-Feinlernziel
        rahmenplan.stream().filter(l -> l.getKind() == KindEnum.KIND_BROAD)
                .forEach(g -> assertEquals(1, lernzielDAO.countByParentId(g.getId()), g.getTitle()));
    }

    @Test
    @Transactional
    void learnersGetAllLernzieleAndImportRunsOnlyOnce() throws Exception {
        long vorher = lernzielDAO.count();
        initializer.run(new DefaultApplicationArguments());
        assertEquals(vorher, lernzielDAO.count(), "Rahmenplan darf nicht doppelt eingespielt werden");

        for (Person azubi : personDAO.findAll().stream().filter(p -> p.getRole().isLearner()).toList()) {
            for (Lernziel l : rahmenplanLernziele()) {
                assertTrue(personLernzielDAO.existsByPersonAndLernziel(azubi, l), azubi.getFullName() + ": " + l.getTitle());
            }
        }
    }

    /** Alle Lernziele aus dem Rahmenplan: Richt-/Groblernziele mit Kennung (A01, A01a …) und deren Feinlernziele. */
    private List<Lernziel> rahmenplanLernziele() {
        List<Lernziel> alle = lernzielDAO.findAll();
        List<Long> grobIds = alle.stream()
                .filter(l -> l.getKind() == KindEnum.KIND_BROAD && l.getTitle().matches("[ABF]\\d{2}[a-z] .*"))
                .map(Lernziel::getId).toList();
        return alle.stream()
                .filter(l -> l.getTitle().matches("[ABF]\\d{2}[a-z]? .*") || grobIds.contains(l.getParentId()))
                .toList();
    }

    private static long count(List<Lernziel> lernziele, KindEnum kind) {
        return lernziele.stream().filter(l -> l.getKind() == kind).count();
    }
}
