package de.fisp.azubiprojekt.util;

/** Bearbeitungsstand eines Lernziels fuer eine bestimmte Person. */
public enum StatusEnum {
    STATUS_UNFINISHED("Offen"),
    STATUS_IN_PROGRESS("In Bearbeitung"),
    STATUS_DONE("Erreicht");

    private final String label;

    StatusEnum(String label) {
        this.label = label;
    }

    public String getLabel() {
        return label;
    }
}
