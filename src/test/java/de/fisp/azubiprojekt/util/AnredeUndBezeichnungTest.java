package de.fisp.azubiprojekt.util;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/** Anrede aus dem Vornamen und Bezeichnungen ohne Gendern. */
class AnredeUndBezeichnungTest {

    @Test
    void anredeWirdAusDemVornamenVermutet() {
        for (String frau : new String[]{"Petra", "Erika", "Simona", "Nadine", "Marie", "Karin", "Anna-Lena", "Ines", " Sophie "}) {
            assertThat(AnredeEnum.vermuten(frau)).as(frau).isEqualTo(AnredeEnum.FRAU);
        }
        for (String herr : new String[]{"Kai", "Max", "Justin", "Martin", "Luca", "Uwe", "Andre", "Hans Peter", "Test", ""}) {
            assertThat(AnredeEnum.vermuten(herr)).as(herr).isEqualTo(AnredeEnum.HERR);
        }
        assertThat(AnredeEnum.vermuten(null)).isEqualTo(AnredeEnum.HERR);
    }

    @Test
    void gegenderteBezeichnungenPassenZurAnrede() {
        assertThat(Bezeichnung.fuer("Fachinformatiker/in Anwendungsentwicklung", AnredeEnum.HERR))
                .isEqualTo("Fachinformatiker Anwendungsentwicklung");
        assertThat(Bezeichnung.fuer("Fachinformatiker/in Anwendungsentwicklung", AnredeEnum.FRAU))
                .isEqualTo("Fachinformatikerin Anwendungsentwicklung");
        assertThat(Bezeichnung.fuer("Informatiker*in", AnredeEnum.FRAU)).isEqualTo("Informatikerin");
        assertThat(Bezeichnung.fuer("Auszubildende/r", AnredeEnum.HERR)).isEqualTo("Auszubildender");
        assertThat(Bezeichnung.fuer("Auszubildende/r", AnredeEnum.FRAU)).isEqualTo("Auszubildende");
        assertThat(Bezeichnung.fuer("Duale/r Student/in", AnredeEnum.FRAU)).isEqualTo("Duale Studentin");
        assertThat(Bezeichnung.fuer("Team der Ausbilder/innen", AnredeEnum.HERR)).isEqualTo("Team der Ausbilder");
        // Das erste Wort wird an die Anrede angeglichen
        assertThat(Bezeichnung.fuer("Informatiker", AnredeEnum.FRAU)).isEqualTo("Informatikerin");
        assertThat(Bezeichnung.fuer("Fachinformatikerin Anwendungsentwicklung", AnredeEnum.HERR))
                .isEqualTo("Fachinformatiker Anwendungsentwicklung");
        assertThat(Bezeichnung.fuer("Wirtschaftsinformatikerin", AnredeEnum.FRAU)).isEqualTo("Wirtschaftsinformatikerin");
        // Andere Texte bleiben, wie sie sind
        assertThat(Bezeichnung.fuer("Kaufmann für Büromanagement", AnredeEnum.FRAU)).isEqualTo("Kaufmann für Büromanagement");
        assertThat(Bezeichnung.gegendert("Fachinformatikerin Anwendungsentwicklung")).isFalse();
        assertThat(Bezeichnung.gegendert("Fachinformatiker/in")).isTrue();
    }

    @Test
    void rollenbezeichnungRichtetSichNachDerAnrede() {
        assertThat(RoleEnum.ROLE_TRAINER.getLabel(AnredeEnum.FRAU)).isEqualTo("Ausbilderin");
        assertThat(RoleEnum.ROLE_TRAINER.getLabel(AnredeEnum.HERR)).isEqualTo("Ausbilder");
        assertThat(RoleEnum.ROLE_STUDENT.getLabel(AnredeEnum.FRAU)).isEqualTo("Duale Studentin");
        assertThat(RoleEnum.ROLE_TRAINEE.getLabel(null)).isEqualTo("Auszubildender");
    }
}
