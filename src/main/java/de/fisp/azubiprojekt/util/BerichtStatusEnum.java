package de.fisp.azubiprojekt.util;

/** Freigabe-Status eines Wochenberichts. */
public enum BerichtStatusEnum {
    /** Azubi bearbeitet die Woche noch. */
    ENTWURF("Entwurf"),
    /** Azubi hat die Woche zur Pruefung eingereicht – keine Bearbeitung mehr moeglich. */
    EINGEREICHT("Eingereicht"),
    /** Ausbilder hat die Woche abgenommen (Name bleibt wegen gespeicherter Daten ABGEZEICHNET). */
    ABGEZEICHNET("Abgenommen"),
    /** Ausbilder hat die Woche mit Kommentar zurueckgegeben – Azubi kann nachbessern. */
    ABGELEHNT("Zur Überarbeitung");

    private final String label;

    BerichtStatusEnum(String label) {
        this.label = label;
    }

    public String getLabel() {
        return label;
    }

    public boolean isEditable() {
        return this == ENTWURF || this == ABGELEHNT;
    }
}
