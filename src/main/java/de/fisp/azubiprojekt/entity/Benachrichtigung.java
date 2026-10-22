package de.fisp.azubiprojekt.entity;

import de.fisp.azubiprojekt.util.BenachrichtigungTypEnum;
import jakarta.persistence.*;

import java.time.LocalDateTime;

/**
 * Nachricht an eine Person (Glocke oben rechts), z. B. "Berichtsheft abgegeben" oder "Korrekturbedarf".
 * {@link #bezug} verknuepft Nachrichten mit ihrem Anlass (z. B. {@code wochenbericht:12}), damit sie
 * automatisch als gelesen gelten, sobald der Anlass erledigt ist, und Erinnerungen nicht doppelt entstehen.
 */
@Entity
@Table(name = "benachrichtigung", indexes = @Index(columnList = "empfaenger_id, gelesen"))
public class Benachrichtigung {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(optional = false, fetch = FetchType.LAZY)
    @JoinColumn(name = "empfaenger_id")
    private Person empfaenger;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 40)
    private BenachrichtigungTypEnum typ;

    @Column(nullable = false, length = 200)
    private String titel;

    @Column(length = 2000)
    private String text;

    /** Ziel im Frontend, z. B. {@code /berichtsheft?datum=2026-10-19}. */
    @Column(length = 300)
    private String link;

    @Column(length = 100)
    private String bezug;

    @Column(nullable = false)
    private LocalDateTime erstelltAm;

    private boolean gelesen;

    public Benachrichtigung() {
    }

    public Benachrichtigung(Person empfaenger, BenachrichtigungTypEnum typ, String titel, String text, String link,
                            String bezug) {
        this.empfaenger = empfaenger;
        this.typ = typ;
        this.titel = titel;
        this.text = text;
        this.link = link;
        this.bezug = bezug;
        this.erstelltAm = LocalDateTime.now();
    }

    public Long getId() {
        return id;
    }

    public Person getEmpfaenger() {
        return empfaenger;
    }

    public BenachrichtigungTypEnum getTyp() {
        return typ;
    }

    public String getTitel() {
        return titel;
    }

    public String getText() {
        return text;
    }

    public String getLink() {
        return link;
    }

    public String getBezug() {
        return bezug;
    }

    public LocalDateTime getErstelltAm() {
        return erstelltAm;
    }

    public boolean isGelesen() {
        return gelesen;
    }

    public void setGelesen(boolean gelesen) {
        this.gelesen = gelesen;
    }
}
