package de.fisp.azubiprojekt.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Einstellungen fuer den E-Mail-Versand ({@code app.mail.*} in application.properties).
 * Der SMTP-Server selbst wird ueber die Standard-Einstellungen {@code spring.mail.*} konfiguriert.
 *
 * @param enabled        Hauptschalter – false: es werden keine E-Mails verschickt
 * @param versandart     SMTP (spring.mail.*, z. B. Mailhog) oder GRAPH (Microsoft 365, app.mail.graph.*)
 * @param absender       Absenderadresse
 * @param absenderName   Anzeigename des Absenders
 * @param antwortAn      optionale Reply-To-Adresse
 * @param betreffPraefix wird jedem Betreff vorangestellt, z. B. "[Azubi Portal]"
 * @param portalUrl      Adresse des Portals fuer die Buttons in den E-Mails
 */
@ConfigurationProperties(prefix = "app.mail")
public record EmailEigenschaften(boolean enabled, Versandart versandart, String absender, String absenderName,
                                 String antwortAn, String betreffPraefix, String portalUrl, Empfaenger empfaenger,
                                 Erinnerung erinnerung, Graph graph) {

    public enum Versandart {
        SMTP, GRAPH
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
     */
    public record Empfaenger(String musterAzubi, String musterAusbilder, boolean kleinschreibung, String umleitenAn) {
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
