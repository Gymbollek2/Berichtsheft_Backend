package de.fisp.azubiprojekt.service;

/**
 * Prueft die Windows-Anmeldung des Browsers (HTTP "Negotiate": Kerberos, ersatzweise NTLM).
 * Der Browser schickt dabei das Konto des angemeldeten Windows-Benutzers mit – ohne Passwortabfrage.
 */
public interface WindowsAnmeldung {

    /**
     * Verarbeitet ein Token aus dem Header {@code Authorization: Negotiate ...}.
     *
     * @param verbindung Kennung der TCP-Verbindung – NTLM braucht mehrere Schritte auf derselben Verbindung
     * @param token      dekodiertes Token des Browsers
     * @param paket      Verfahren aus dem Header: "Negotiate" (Kerberos, ersatzweise NTLM) oder "NTLM"
     * @return {@link Schritt#fertig()} mit dem Konto {@code DOMAENE\benutzer} oder ein Zwischenschritt
     */
    Schritt pruefe(String verbindung, byte[] token, String paket);

    /** Verwirft einen abgebrochenen Handshake. */
    void abbrechen(String verbindung);

    /**
     * @param fertig       Handshake abgeschlossen
     * @param antwortToken Token fuer den Header {@code WWW-Authenticate: Negotiate ...} (kann fehlen)
     * @param konto        angemeldetes Konto als {@code DOMAENE\benutzer}, {@code null} bei Gast/anonym
     */
    record Schritt(boolean fertig, byte[] antwortToken, String konto) {
    }
}
