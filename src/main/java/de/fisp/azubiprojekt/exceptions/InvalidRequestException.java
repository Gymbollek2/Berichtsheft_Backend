package de.fisp.azubiprojekt.exceptions;

/** Fachlich ungueltige Anfrage, z. B. falscher Status oder doppelte Werte (HTTP 400). */
public class InvalidRequestException extends RuntimeException {

    public InvalidRequestException(String message) {
        super(message);
    }
}
