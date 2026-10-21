package de.fisp.azubiprojekt.exceptions;

/** Wird geworfen, wenn eine angeforderte Ressource nicht existiert (HTTP 404). */
public class ResourceNotFoundException extends RuntimeException {

    public ResourceNotFoundException(String message) {
        super(message);
    }
}
