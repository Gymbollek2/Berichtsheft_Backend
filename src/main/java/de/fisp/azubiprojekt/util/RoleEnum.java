package de.fisp.azubiprojekt.util;

/**
 * Benutzerrollen des Azubi-Portals.
 * <ul>
 *     <li>{@link #ROLE_TRAINEE} – Auszubildende/r: fuehrt das eigene Berichtsheft, sieht eigene Lernziele.</li>
 *     <li>{@link #ROLE_STUDENT} – Duale/r Student/in: Rechte wie Auszubildende.</li>
 *     <li>{@link #ROLE_TRAINER} – Ausbilder/in: darf alles – verwaltet Personen (auch andere Ausbilder) und
 *     Lernziele, nimmt Berichtshefte ab.</li>
 * </ul>
 */
public enum RoleEnum {
    ROLE_TRAINEE("Auszubildende/r"),
    ROLE_STUDENT("Duale/r Student/in"),
    ROLE_TRAINER("Ausbilder/in");

    private final String label;

    RoleEnum(String label) {
        this.label = label;
    }

    public String getLabel() {
        return label;
    }

    /** Rollen, die ein Berichtsheft fuehren und Lernziele zugewiesen bekommen. */
    public boolean isLearner() {
        return this == ROLE_TRAINEE || this == ROLE_STUDENT;
    }

    /** Rollen mit Verwaltungsrechten (Ausbilder). */
    public boolean isStaff() {
        return this == ROLE_TRAINER;
    }
}
