package de.fisp.azubiprojekt.util;

/** Anlass einer Benachrichtigung (bestimmt im Frontend Symbol und Farbe). */
public enum BenachrichtigungTypEnum {
    /** An Ausbilder: Ein Azubi hat ein Berichtsheft abgegeben. */
    BERICHT_ABGEGEBEN,
    /** An den Azubi: Das Berichtsheft wurde abgenommen. */
    BERICHT_ABGEZEICHNET,
    /** An den Azubi: Das Berichtsheft wurde zur Ueberarbeitung zurueckgegeben. */
    BERICHT_ZURUECKGEGEBEN,
    /** An den Azubi: Das Berichtsheft der Vorwoche ist noch nicht abgegeben. */
    ERINNERUNG_ABGABE,
    /** An den Azubi: Ein Ausbilder hat ein Lernziel als erreicht markiert. */
    LERNZIEL_ERREICHT,
    /** An neue Personen: Zugang wurde angelegt. */
    WILLKOMMEN
}
