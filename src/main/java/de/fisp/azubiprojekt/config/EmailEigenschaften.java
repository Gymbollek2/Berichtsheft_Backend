package de.fisp.azubiprojekt.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Einstellungen fuer den E-Mail-Versand ({@code app.mail.*} in application.properties).
 * Der SMTP-Server selbst wird ueber die Standard-Einstellungen {@code spring.mail.*} konfiguriert.
 *
 * @param enabled        Hauptschalter – false: es werden keine E-Mails verschickt
 * @param versandart     SMTP (spring.mail.*, z. B. Mailhog), GRAPH (Microsoft 365, app.mail.graph.*) oder
 *                       EXCHANGE (eigener Exchange-Server per SMTP mit Anmeldung, Kopie per IMAP, app.mail.exchange.*)
 * @param absender       Absenderadresse
 * @param absenderName   Anzeigename des Absenders
 * @param antwortAn      optionale Reply-To-Adresse
 * @param betreffPraefix wird jedem Betreff vorangestellt, z. B. "[Azubi Portal]"
 * @param portalUrl      Adresse des Portals fuer die Buttons in den E-Mails
 */
@ConfigurationProperties(prefix = "app.mail")
public record EmailEigenschaften(boolean enabled, Versandart versandart, String absender, String absenderName,
                                 String antwortAn, String betreffPraefix, String portalUrl, Empfaenger empfaenger,
                                 Erinnerung erinnerung, Graph graph, Exchange exchange) {

    public enum Versandart {
        SMTP, GRAPH, EXCHANGE
    }

    /**
     * Versand ueber einen eigenen Exchange-Server: Die Mail geht per SMTP mit Anmeldung (Client-Submission, meist
     * Port 587 mit STARTTLS) raus, danach wird per IMAP eine Kopie in den Ordner "Gesendete Elemente" gelegt –
     * Exchange macht das bei SMTP-Versand nicht selbst.
     *
     * @param smtpHost             SMTP-Server, z. B. mail.firma.de
     * @param smtpPort             587 (STARTTLS) oder 465 (SSL)
     * @param starttls             STARTTLS verlangen (Port 587)
     * @param ssl                  SSL/TLS von Anfang an (Port 465)
     * @param benutzername         Anmeldename des Postfachs, z. B. ausbildung@firma.de oder FIRMA\ausbildung
     * @param passwort             Passwort des Postfachs – nur per Umgebungsvariable setzen
     * @param sslTrust             Hosts, deren Zertifikat ohne Pruefung vertraut wird (leer = normale Pruefung)
     * @param sslServernamePruefen pruefen, ob der Servername im Zertifikat steht (nur fuer Sonderfaelle abschalten)
     * @param timeoutMs            Zeitlimit fuer Verbindung und Antworten in Millisekunden
     * @param ehloName             Name, mit dem sich die Anwendung beim SMTP-Server meldet (leer = Rechnername,
     *                             ohne DNS-Abfrage)
     * @param imGesendetSpeichern  Kopie per IMAP in den Ordner {@code gesendetOrdner} legen
     * @param imapHost             IMAP-Server (leer = wie smtpHost)
     * @param imapPort             993 (SSL) oder 143 (STARTTLS)
     * @param imapSsl              IMAP ueber SSL/TLS (Port 993), sonst STARTTLS
     * @param gesendetOrdner       Ordner fuer die Kopie, bei deutschem Exchange "Gesendete Elemente"
     */
    public record Exchange(String smtpHost, int smtpPort, boolean starttls, boolean ssl, String benutzername,
                           String passwort, String sslTrust, boolean sslServernamePruefen, int timeoutMs,
                           String ehloName, boolean imGesendetSpeichern, String imapHost, int imapPort, boolean imapSsl,
                           String gesendetOrdner) {
    }

    /**
     * Versand ueber Microsoft Graph mit OAuth2 (Client-Credentials einer App-Registrierung in Microsoft Entra ID).
     *
     * @param tenantId            Verzeichnis-ID (Mandant) bzw. Domain, z. B. cimbollek.de
     * @param clientId            Anwendungs-ID der App-Registrierung
     * @param clientSecret        geheimer Clientschluessel – nur per Umgebungsvariable setzen
     * @param postfach            Postfach, aus dem gesendet wird (leer = Absenderadresse)
     * @param imGesendetSpeichern Kopie im Ordner "Gesendete Elemente" des Postfachs ablegen
     * @param loginUrl            Anmeldeadresse von Microsoft Entra ID
     * @param apiUrl              Adresse der Graph-API
     * @param scope               angeforderte Berechtigung
     * @param timeoutMs           Zeitlimit je Anfrage in Millisekunden
     */
    public record Graph(String tenantId, String clientId, String clientSecret, String postfach,
                        boolean imGesendetSpeichern, String loginUrl, String apiUrl, String scope, int timeoutMs) {
    }

    /**
     * Bildung der Empfaengeradresse aus dem Namen der Person.
     *
     * @param musterAzubi     z. B. "{vorname}.{nachname}@Azubi.de" (Platzhalter: {vorname}, {nachname}, {benutzername})
     * @param musterAusbilder dasselbe fuer Ausbilder
     * @param kleinschreibung Adresse komplett kleinschreiben
     * @param umleitenAn      zum Testen: alle E-Mails an diese Adresse statt an die echten Empfaenger
     * @param kontoDomain     Domain fuer die Mail "Account erstellt": benutzername@domain bzw. Vorname.Nachname@domain
     */
    public record Empfaenger(String musterAzubi, String musterAusbilder, boolean kleinschreibung, String umleitenAn,
                             String kontoDomain) {
    }

    /**
     * Automatische Erinnerungen.
     *
     * @param enabled          Erinnerungen per E-Mail verschicken
     * @param tageBisAbgabe    Azubi: so viele Tage nach dem Freitag der Woche ist sie ueberfaellig
     * @param tageBisAbnahme   Ausbilder: so viele Tage nach der Abgabe ist die Abnahme ueberfaellig
     * @param maxWochenZurueck aeltere Wochen werden nicht mehr angemahnt
     */
    public record Erinnerung(boolean enabled, int tageBisAbgabe, int tageBisAbnahme, int maxWochenZurueck) {
    }
}
