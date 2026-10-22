package de.fisp.azubiprojekt.entity;

import de.fisp.azubiprojekt.util.EintragArtEnum;
import jakarta.persistence.*;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

/**
 * Ein Tageseintrag im Berichtsheft. Mehrere Tageseintraege einer Kalenderwoche werden
 * gemeinsam ueber einen {@link Wochenbericht} eingereicht und abgenommen.
 */
@Entity
@Table(name = "berichtsheft",
        uniqueConstraints = @UniqueConstraint(columnNames = {"person_id", "date"}))
public class Berichtsheft {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    private LocalDate date;

    private double hours;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private EintragArtEnum art = EintragArtEnum.BETRIEB;

    @Column(length = 4000)
    private String description;

    @ManyToOne(optional = false, fetch = FetchType.LAZY)
    @JoinColumn(name = "person_id")
    private Person person;

    /** Lernziele, die an diesem Tag bearbeitet wurden. */
    @ManyToMany
    @JoinTable(
            name = "berichtsheft_lernziel",
            joinColumns = @JoinColumn(name = "berichtsheft_id"),
            inverseJoinColumns = @JoinColumn(name = "lernziel_id")
    )
    private List<Lernziel> qualifications = new ArrayList<>();

    /** Zeitpunkt der letzten Speicherung (fuer die Anzeige "zuletzt gespeichert"). */
    private LocalDateTime updatedAt;

    public Berichtsheft() {
    }

    public Berichtsheft(LocalDate date, double hours, Person person, String description) {
        this.date = date;
        this.hours = hours;
        this.person = person;
        this.description = description;
    }

    public Long getId() {
        return id;
    }

    public LocalDate getDate() {
        return date;
    }

    public void setDate(LocalDate date) {
        this.date = date;
    }

    public double getHours() {
        return hours;
    }

    public void setHours(double hours) {
        this.hours = hours;
    }

    public EintragArtEnum getArt() {
        return art;
    }

    public void setArt(EintragArtEnum art) {
        this.art = art;
    }

    public String getDescription() {
        return description;
    }

    public void setDescription(String description) {
        this.description = description;
    }

    public Person getPerson() {
        return person;
    }

    public void setPerson(Person person) {
        this.person = person;
    }

    public List<Lernziel> getQualifications() {
        return qualifications;
    }

    public void setQualifications(List<Lernziel> qualifications) {
        this.qualifications = new ArrayList<>(qualifications);
    }

    public LocalDateTime getUpdatedAt() {
        return updatedAt;
    }

    public void setUpdatedAt(LocalDateTime updatedAt) {
        this.updatedAt = updatedAt;
    }
}
