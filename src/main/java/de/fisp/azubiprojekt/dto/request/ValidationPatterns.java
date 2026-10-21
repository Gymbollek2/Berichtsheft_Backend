package de.fisp.azubiprojekt.dto.request;

/** Gemeinsame Validierungsregeln fuer Benutzerdaten. */
public final class ValidationPatterns {

    /** Mindestens 8 Zeichen, davon mindestens ein Buchstabe und eine Ziffer. */
    public static final String PASSWORD = "^(?=.*[A-Za-zÄÖÜäöüß])(?=.*\\d).{8,100}$";
    public static final String PASSWORD_MESSAGE = "muss mindestens 8 Zeichen mit Buchstaben und Ziffern enthalten";

    /** 3–50 Zeichen: Kleinbuchstaben, Ziffern, Punkt, Bindestrich, Unterstrich. */
    public static final String USERNAME = "^[a-z0-9._-]{3,50}$";
    public static final String USERNAME_MESSAGE = "darf nur Kleinbuchstaben, Ziffern, Punkt, - und _ enthalten (3–50 Zeichen)";

    private ValidationPatterns() {
    }
}
