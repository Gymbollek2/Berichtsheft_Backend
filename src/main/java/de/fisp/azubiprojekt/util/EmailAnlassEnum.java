package de.fisp.azubiprojekt.util;

/** Anlaesse fuer automatische E-Mails, die je Empfaenger und Bezug nur einmal verschickt werden. */
public enum EmailAnlassEnum {
    /** Azubi: Woche ist seit ueber einer Woche faellig, aber nicht abgegeben. */
    ERINNERUNG_ABGABE,
    /** Ausbilder: abgegebene Woche wartet seit ueber einer Woche auf die Abnahme. */
    ERINNERUNG_ABNAHME
}
