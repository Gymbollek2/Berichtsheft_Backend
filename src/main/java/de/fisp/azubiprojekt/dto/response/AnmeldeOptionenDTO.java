package de.fisp.azubiprojekt.dto.response;

/**
 * Anmeldearten fuer die Login-Seite.
 *
 * @param sso           Windows-Anmeldung (Single Sign-on) ist eingeschaltet
 * @param passwortLogin Anmeldung mit Benutzername und Passwort ist erlaubt
 */
public record AnmeldeOptionenDTO(boolean sso, boolean passwortLogin) {
}
