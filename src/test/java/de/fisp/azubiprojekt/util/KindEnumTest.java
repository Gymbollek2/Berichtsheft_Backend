package de.fisp.azubiprojekt.util;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class KindEnumTest {

    @Test
    void parseAcceptsEnumNamesLabelsAndShortForms() {
        assertEquals(KindEnum.KIND_BROAD, KindEnum.parse("KIND_BROAD"));
        assertEquals(KindEnum.KIND_GENERAL, KindEnum.parse("Richtlernziel"));
        assertEquals(KindEnum.KIND_SPECIFIC, KindEnum.parse("fein"));
        assertEquals(KindEnum.KIND_BROAD, KindEnum.parse("  Grob "));
    }

    @Test
    void hierarchyRichtGrobFein() {
        assertEquals(null, KindEnum.KIND_GENERAL.requiredParentKind());
        assertEquals(KindEnum.KIND_GENERAL, KindEnum.KIND_BROAD.requiredParentKind());
        assertEquals(KindEnum.KIND_BROAD, KindEnum.KIND_SPECIFIC.requiredParentKind());
    }

    @Test
    void parseRejectsUnknownValues() {
        assertThrows(IllegalArgumentException.class, () -> KindEnum.parse("Superziel"));
        assertThrows(IllegalArgumentException.class, () -> KindEnum.parse(""));
    }
}
