package de.fisp.azubiprojekt.service;

import java.util.ArrayList;
import java.util.List;

/**
 * Inhalt einer E-Mail, unabhaengig von der Darstellung. {@link EmailService} macht daraus eine HTML-Mail
 * (mit Button) und eine Textfassung. In Texten markiert {@code **...**} eine Hervorhebung.
 */
public final class EmailInhalt {

    /** Farbe des Status-Hinweises oben in der Mail. */
    public enum Ton {
        ERFOLG("#2f6b4f", "#e5f1eb"),
        ERINNERUNG("#84590a", "#fbf0d9"),
        KORREKTUR("#9b1c2c", "#f8e3e6");

        final String farbe;
        final String hintergrund;

        Ton(String farbe, String hintergrund) {
            this.farbe = farbe;
            this.hintergrund = hintergrund;
        }
    }

    sealed interface Block permits Absatz, Liste, Zitat {
    }

    record Absatz(String text) implements Block {
    }

    record Liste(List<String> punkte) implements Block {
    }

    record Zitat(String titel, String text) implements Block {
    }

    private final String betreff;
    private String ueberschrift;
    private String status;
    private Ton ton = Ton.ERINNERUNG;
    private String buttonText;
    private String buttonPfad;
    private final List<Block> bloecke = new ArrayList<>();

    private EmailInhalt(String betreff) {
        this.betreff = betreff;
        this.ueberschrift = betreff;
    }

    public static EmailInhalt mitBetreff(String betreff) {
        return new EmailInhalt(betreff);
    }

    public EmailInhalt ueberschrift(String ueberschrift) {
        this.ueberschrift = ueberschrift;
        return this;
    }

    public EmailInhalt status(String status, Ton ton) {
        this.status = status;
        this.ton = ton;
        return this;
    }

    public EmailInhalt absatz(String text) {
        bloecke.add(new Absatz(text));
        return this;
    }

    public EmailInhalt liste(List<String> punkte) {
        bloecke.add(new Liste(List.copyOf(punkte)));
        return this;
    }

    public EmailInhalt zitat(String titel, String text) {
        bloecke.add(new Zitat(titel, text));
        return this;
    }

    /** Button, der ins Portal fuehrt. {@code pfad} ist relativ zur Portal-Adresse, z. B. "/berichtsheft?datum=…". */
    public EmailInhalt button(String text, String pfad) {
        this.buttonText = text;
        this.buttonPfad = pfad;
        return this;
    }

    public String getBetreff() {
        return betreff;
    }

    public String getUeberschrift() {
        return ueberschrift;
    }

    public String getStatus() {
        return status;
    }

    public Ton getTon() {
        return ton;
    }

    public String getButtonText() {
        return buttonText;
    }

    public String getButtonPfad() {
        return buttonPfad;
    }

    List<Block> getBloecke() {
        return bloecke;
    }
}
