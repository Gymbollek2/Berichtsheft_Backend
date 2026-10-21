package de.fisp.azubiprojekt.entity;

import de.fisp.azubiprojekt.util.StatusEnum;
import jakarta.persistence.*;

import java.time.LocalDateTime;

/** Zuordnung eines Lernziels zu einer Person inkl. Bearbeitungsstand. */
@Entity
@Table(name = "person_lernziel",
        uniqueConstraints = @UniqueConstraint(columnNames = {"person_id", "lernziel_id"}))
public class PersonLernziel {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(optional = false, fetch = FetchType.LAZY)
    @JoinColumn(name = "person_id")
    private Person person;

    @ManyToOne(optional = false)
    @JoinColumn(name = "lernziel_id")
    private Lernziel lernziel;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private StatusEnum status;

    private LocalDateTime updatedAt;

    public PersonLernziel() {
    }

    public PersonLernziel(Person person, Lernziel lernziel, StatusEnum status) {
        this.person = person;
        this.lernziel = lernziel;
        this.status = status;
        this.updatedAt = LocalDateTime.now();
    }

    public Long getId() {
        return id;
    }

    public Person getPerson() {
        return person;
    }

    public void setPerson(Person person) {
        this.person = person;
    }

    public Lernziel getLernziel() {
        return lernziel;
    }

    public void setLernziel(Lernziel lernziel) {
        this.lernziel = lernziel;
    }

    public StatusEnum getStatus() {
        return status;
    }

    public void setStatus(StatusEnum status) {
        this.status = status;
        this.updatedAt = LocalDateTime.now();
    }

    public LocalDateTime getUpdatedAt() {
        return updatedAt;
    }
}
