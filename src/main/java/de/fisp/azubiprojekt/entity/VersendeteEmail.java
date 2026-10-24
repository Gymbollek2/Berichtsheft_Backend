package de.fisp.azubiprojekt.entity;

import de.fisp.azubiprojekt.util.EmailAnlassEnum;
import jakarta.persistence.*;

import java.time.LocalDateTime;

/**
 * Merkt sich verschickte Erinnerungs-E-Mails, damit jede nur einmal rausgeht.
 * Die Person wird nur per ID referenziert – so blockiert der Eintrag das Loeschen einer Person nicht.
 */
@Entity
@Table(name = "versendete_email",
        uniqueConstraints = @UniqueConstraint(columnNames = {"person_id", "anlass", "bezug"}))
public class VersendeteEmail {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "person_id", nullable = false)
    private Long personId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private EmailAnlassEnum anlass;

    /** Worauf sich die E-Mail bezieht, z. B. "woche:2026-10-12" oder "wochenbericht:5:2026-10-13T08:15". */
    @Column(nullable = false)
    private String bezug;

    @Column(nullable = false)
    private LocalDateTime versendetAm;

    public VersendeteEmail() {
    }

    public VersendeteEmail(Long personId, EmailAnlassEnum anlass, String bezug) {
        this.personId = personId;
        this.anlass = anlass;
        this.bezug = bezug;
        this.versendetAm = LocalDateTime.now();
    }

    public Long getId() {
        return id;
    }

    public Long getPersonId() {
        return personId;
    }

    public EmailAnlassEnum getAnlass() {
        return anlass;
    }

    public String getBezug() {
        return bezug;
    }

    public LocalDateTime getVersendetAm() {
        return versendetAm;
    }
}
