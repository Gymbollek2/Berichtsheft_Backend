package de.fisp.azubiprojekt.util;

import java.util.Locale;
import java.util.Set;

/**
 * Anrede einer Person. Danach richten sich Rollen- und Berufsbezeichnungen (Ausbilder/Ausbilderin,
 * Fachinformatiker/Fachinformatikerin) – im Portal wird nicht gegendert.
 */
public enum AnredeEnum {
    HERR("Herr"),
    FRAU("Frau");

    /** Weibliche Vornamen, die nicht auf -a oder -e enden. */
    private static final Set<String> WEIBLICH = Set.of(
            "astrid", "aylin", "beatrix", "birgit", "carmen", "dagmar", "doris", "edith", "elif", "elisabeth",
            "ellen", "emily", "esther", "gertrud", "gudrun", "gwen", "heidi", "heidrun", "helen", "ines", "ingeborg",
            "ingrid", "irem", "iris", "irmgard", "isabel", "jasmin", "jennifer", "jenny", "jill", "judith", "karin",
            "kathrin", "katrin", "kerstin", "kim", "kirsten", "lilian", "lilly", "lily", "liv", "maren", "margit",
            "marion", "mary", "miriam", "nadin", "nancy", "nell", "romy", "ruth", "selin", "sigrid", "susan",
            "vivien", "waltraud", "yasmin", "zoey");

    /** Maennliche Vornamen, die auf -a oder -e enden. */
    private static final Set<String> MAENNLICH = Set.of(
            "andre", "dante", "daniele", "dave", "dima", "eike", "elia", "george", "giuseppe", "hamza", "hauke",
            "ilja", "isa", "jake", "janne", "jesse", "jona", "joshua", "kalle", "kosta", "lasse", "luca", "luka",
            "luke", "malte", "mattia", "mika", "mike", "musa", "mustafa", "nikita", "ole", "ove", "pierre", "rene",
            "sascha", "steve", "uwe", "vova");

    private final String label;

    AnredeEnum(String label) {
        this.label = label;
    }

    public String getLabel() {
        return label;
    }

    /**
     * Vermutet die Anrede aus dem Vornamen – fuer bestehende Personen ohne Anrede und als Rueckfall, wenn beim
     * Anlegen keine angegeben wird. Bekannte Ausnahmen stehen in den Listen, sonst gilt: endet der Vorname auf
     * -a oder -e (Petra, Simona, Nadine, Marie), ist es eine Frau, sonst ein Herr (Kai, Max, Justin).
     * Korrigieren laesst es sich jederzeit unter Personen.
     */
    public static AnredeEnum vermuten(String vorname) {
        if (vorname == null || vorname.isBlank()) {
            return HERR;
        }
        // Bei Doppelnamen ("Anna-Lena", "Hans Peter") zaehlt der erste Teil
        String erster = vorname.trim().toLowerCase(Locale.GERMAN).split("[\\s-]+")[0];
        if (MAENNLICH.contains(erster)) {
            return HERR;
        }
        if (WEIBLICH.contains(erster)) {
            return FRAU;
        }
        return erster.endsWith("a") || erster.endsWith("e") ? FRAU : HERR;
    }
}
