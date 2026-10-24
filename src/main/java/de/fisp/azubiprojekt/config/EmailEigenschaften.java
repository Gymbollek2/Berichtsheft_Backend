package de.fisp.azubiprojekt.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Einstellungen fuer den E-Mail-Versand ({@code app.mail.*} in application.properties).
 * Der SMTP-Server selbst wird ueber die Standard-Einstellungen {@code spring.mail.*} konfiguriert.
 *
 * @param enabled        Hauptschalter – false: es werden keine E-Mails verschickt
 * @param absender       Absenderadresse
 * @param absenderName   Anzeigename des Absenders
 * @param antwortAn      optionale Reply-To-Adresse
 * @param betreffPraefix wird jedem Betreff vorangestellt, z. B. "[Azubi Portal]"
 * @param portalUrl      Adresse des Portals fuer die Buttons in den E-Mails
 */
@ConfigurationProperties(prefix = "app.mail")
public record EmailEigenschaften(boolean enabled, String absender, String absenderName, String antwortAn,
                                 String betreffPraefix, String portalUrl, Empfaenger empfaenger, Erinnerung erinnerung) {

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
