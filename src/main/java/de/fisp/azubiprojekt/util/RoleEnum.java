package de.fisp.azubiprojekt.util;

/**
 * Benutzerrollen des Azubi-Portals.
 * <ul>
 *     <li>{@link #ROLE_TRAINEE} – Auszubildende/r: fuehrt das eigene Berichtsheft, sieht eigene Lernziele.</li>
 *     <li>{@link #ROLE_STUDENT} – Duale/r Student/in: Rechte wie Auszubildende.</li>
 *     <li>{@link #ROLE_TRAINER} – Ausbilder/in: verwaltet Azubis und Lernziele, zeichnet Berichtshefte ab.</li>
 *     <li>{@link #ROLE_ADMIN} – Administrator/in: alles, inkl. Verwaltung von Ausbildern und Admins.</li>
 * </ul>
 */
public enum RoleEnum {
    ROLE_TRAINEE("Auszubildende/r"),
    ROLE_STUDENT("Duale/r Student/in"),
    ROLE_TRAINER("Ausbilder/in"),
    ROLE_ADMIN("Administrator/in");

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

    /** Rollen mit Verwaltungsrechten (Ausbilder und Admin). */
    public boolean isStaff() {
        return this == ROLE_TRAINER || this == ROLE_ADMIN;
    }
}
