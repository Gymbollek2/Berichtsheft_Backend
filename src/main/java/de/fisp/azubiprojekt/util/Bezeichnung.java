package de.fisp.azubiprojekt.util;

import java.util.regex.Pattern;

/**
 * Macht aus gegenderten Bezeichnungen die passende Form zur Anrede:
 * "Fachinformatiker/in Anwendungsentwicklung" wird zu "Fachinformatiker Anwendungsentwicklung" (Herr) bzw.
 * "Fachinformatikerin Anwendungsentwicklung" (Frau), "Auszubildende/r" zu "Auszubildender" bzw. "Auszubildende".
 */
public final class Bezeichnung {

    /** Fachinformatiker/in, Student/in, Informatiker*in, Informatiker:in */
    private static final Pattern IN = Pattern.compile("(\\p{L}+)[/*:_](?:in)\\b");
    /** Ausbilder/innen, Kund*innen */
    private static final Pattern INNEN = Pattern.compile("(\\p{L}+)[/*:_](?:innen)\\b");
    /** Auszubildende/r, Duale/r */
    private static final Pattern R = Pattern.compile("(\\p{L}+e)/r\\b");
    /** Erstes Wort einer Berufsbezeichnung auf -er bzw. -erin: "Informatiker", "Fachinformatikerin Anwendungs…" */
    private static final Pattern ERSTES_WORT = Pattern.compile("^(\\p{L}*er)(?:in)?(?=\\s|$)");

    private Bezeichnung() {
    }

    /**
     * Passt den Text an die Anrede an; ohne Anrede (null) wird die maennliche Form genommen. Neben gegenderten
     * Formen wird auch das erste Wort angeglichen ("Informatiker" wird bei Frau zu "Informatikerin" und umgekehrt).
     */
    public static String fuer(String text, AnredeEnum anrede) {
        if (text == null) {
            return null;
        }
        boolean frau = anrede == AnredeEnum.FRAU;
        String s = INNEN.matcher(text).replaceAll("$1");
        s = IN.matcher(s).replaceAll(frau ? "$1in" : "$1");
        s = R.matcher(s).replaceAll(frau ? "$1" : "$1r");
        return ERSTES_WORT.matcher(s).replaceFirst(frau ? "$1in" : "$1");
    }

    /** true, wenn der Text noch gegenderte Formen enthaelt. */
    public static boolean gegendert(String text) {
        return text != null && (IN.matcher(text).find() || INNEN.matcher(text).find() || R.matcher(text).find());
    }
}
