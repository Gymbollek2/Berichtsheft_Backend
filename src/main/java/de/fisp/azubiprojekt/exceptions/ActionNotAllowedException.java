package de.fisp.azubiprojekt.exceptions;

/** Der angemeldete Benutzer darf diese Aktion nicht ausfuehren (HTTP 403). */
public class ActionNotAllowedException extends RuntimeException {

    public ActionNotAllowedException(String message) {
        super(message);
    }
}
