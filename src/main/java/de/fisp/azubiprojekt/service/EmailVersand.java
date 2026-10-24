package de.fisp.azubiprojekt.service;

/**
 * Versandweg fuer E-Mails, gewaehlt ueber {@code app.mail.versandart}:
 * {@link SmtpEmailVersand} (smtp) oder {@link GraphEmailVersand} (graph, Microsoft 365).
 */
public interface EmailVersand {

    /** Kurzname fuer das Protokoll, z. B. "SMTP". */
    String name();

    /** Uebergibt die Mail an den Mailserver bzw. Microsoft 365; jeder Fehler wird als Exception gemeldet. */
    void senden(VorbereiteteEmail email) throws Exception;
}
