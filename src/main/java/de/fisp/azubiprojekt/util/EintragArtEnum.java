package de.fisp.azubiprojekt.util;

/** Wo bzw. wie ein Ausbildungstag verbracht wurde. */
public enum EintragArtEnum {
    BETRIEB("Betrieb"),
    SCHULE("Berufsschule"),
    URLAUB("Urlaub"),
    KRANK("Krank"),
    FEIERTAG("Feiertag"),
    SONSTIGES("Sonstiges");

    private final String label;

    EintragArtEnum(String label) {
        this.label = label;
    }

    public String getLabel() {
        return label;
    }
}
