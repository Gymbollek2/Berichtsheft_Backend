package de.fisp.azubiprojekt.util;

import java.text.Normalizer;
import java.util.Arrays;
import java.util.Locale;

/**
 * Art eines Lernziels. Die Reihenfolge entspricht der Hierarchie:
 * Richtlernziel &gt; Groblernziel &gt; Feinlernziel.
 */
public enum KindEnum {
    KIND_GENERAL("Richtlernziel"),
    KIND_BROAD("Groblernziel"),
    KIND_SPECIFIC("Feinlernziel");

    private final String label;

    KindEnum(String label) {
        this.label = label;
    }

    public String getLabel() {
        return label;
    }

    /**
     * Art, die das uebergeordnete Lernziel haben muss:
     * Richtlernziel → keines, Groblernziel → Richtlernziel, Feinlernziel → Groblernziel.
     */
    public KindEnum requiredParentKind() {
        return switch (this) {
            case KIND_GENERAL -> null;
            case KIND_BROAD -> KIND_GENERAL;
            case KIND_SPECIFIC -> KIND_BROAD;
        };
    }

    /**
     * Erkennt sowohl den Enum-Namen (z. B. {@code KIND_BROAD}) als auch die deutsche
     * Bezeichnung bzw. Kurzform (z. B. {@code Groblernziel}, {@code grob}).
     */
    public static KindEnum parse(String value) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException("Lernziel-Art fehlt");
        }
        String normalized = Normalizer.normalize(value.trim(), Normalizer.Form.NFC).toLowerCase(Locale.GERMAN);
        return Arrays.stream(values())
                .filter(k -> k.name().equalsIgnoreCase(normalized)
                        || k.label.toLowerCase(Locale.GERMAN).equals(normalized)
                        || k.label.toLowerCase(Locale.GERMAN).startsWith(normalized))
                .findFirst()
                .orElseThrow(() -> new IllegalArgumentException("Unbekannte Lernziel-Art: " + value));
    }
}
