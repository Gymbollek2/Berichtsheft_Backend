package de.fisp.azubiprojekt.util;

/**
 * Benutzerrollen des Azubi-Portals.
 * <ul>
 *     <li>{@link #ROLE_TRAINEE} – Auszubildender: fuehrt das eigene Berichtsheft, sieht eigene Lernziele.</li>
 *     <li>{@link #ROLE_STUDENT} – Dualer Student: Rechte wie Auszubildende.</li>
 *     <li>{@link #ROLE_TRAINER} – Ausbilder: darf alles – verwaltet Personen (auch andere Ausbilder) und
 *     Lernziele, nimmt Berichtshefte ab.</li>
 * </ul>
 * Die Bezeichnung richtet sich nach der Anrede der Person ({@link #getLabel(AnredeEnum)}), es wird nicht gegendert.
 */
public enum RoleEnum {
    ROLE_TRAINEE("Auszubildender", "Auszubildende", "Auszubildende"),
    ROLE_STUDENT("Dualer Student", "Duale Studentin", "Duale Studenten"),
    ROLE_TRAINER("Ausbilder", "Ausbilderin", "Ausbilder");

    private final String maennlich;
    private final String weiblich;
    private final String mehrzahl;

    RoleEnum(String maennlich, String weiblich, String mehrzahl) {
        this.maennlich = maennlich;
        this.weiblich = weiblich;
        this.mehrzahl = mehrzahl;
    }

    /** Bezeichnung fuer eine Person mit dieser Anrede, z. B. "Ausbilderin" bei Frau. */
    public String getLabel(AnredeEnum anrede) {
        return anrede == AnredeEnum.FRAU ? weiblich : maennlich;
    }

    /** Mehrzahl fuer Meldungen ohne bestimmte Person, z. B. "Für Duale Studenten ist …". */
    public String getMehrzahl() {
        return mehrzahl;
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
