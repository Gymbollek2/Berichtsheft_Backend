package de.fisp.azubiprojekt.service;

/**
 * Fertig aufbereitete Mail – enthaelt nur noch Text, damit sie ausserhalb der Transaktion verschickt werden kann.
 *
 * @param an              Empfaengeradresse (bei Umleitung die Testadresse)
 * @param anName          Anzeigename des Empfaengers, bei Umleitung null
 * @param urspruenglichAn eigentlicher Empfaenger, wenn die Mail umgeleitet wurde, sonst null
 */
public record VorbereiteteEmail(String an, String anName, String urspruenglichAn, String betreff, String text, String html) {

    /** Dieselbe Mail an eine Testadresse statt an den echten Empfaenger. */
    VorbereiteteEmail umgeleitetAn(String adresse) {
        return new VorbereiteteEmail(adresse, null, an, betreff, text, html);
    }

    public boolean istUmgeleitet() {
        return urspruenglichAn != null;
    }
}
