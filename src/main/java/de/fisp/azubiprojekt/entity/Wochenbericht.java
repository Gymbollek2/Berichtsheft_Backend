package de.fisp.azubiprojekt.entity;

import de.fisp.azubiprojekt.util.BerichtStatusEnum;
import jakarta.persistence.*;

import java.time.LocalDate;
import java.time.LocalDateTime;

/**
 * Freigabe-Klammer fuer eine Kalenderwoche eines Azubis (Abgeben, Abnehmen, Zurueckgeben).
 * {@link #weekStart} ist immer der Montag der Woche.
 */
@Entity
@Table(name = "wochenbericht",
        uniqueConstraints = @UniqueConstraint(columnNames = {"person_id", "week_start"}))
public class Wochenbericht {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(optional = false, fetch = FetchType.LAZY)
    @JoinColumn(name = "person_id")
    private Person person;

    @Column(name = "week_start", nullable = false)
    private LocalDate weekStart;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private BerichtStatusEnum status = BerichtStatusEnum.ENTWURF;

    @Column(length = 2000)
    private String kommentar;

    private String geprueftVon;
    private LocalDateTime eingereichtAm;
    private LocalDateTime geprueftAm;

    public Wochenbericht() {
    }

    public Wochenbericht(Person person, LocalDate weekStart) {
        this.person = person;
        this.weekStart = weekStart;
    }

    public Long getId() {
        return id;
    }

    public Person getPerson() {
        return person;
    }

    public LocalDate getWeekStart() {
        return weekStart;
    }

    public BerichtStatusEnum getStatus() {
        return status;
    }

    public void setStatus(BerichtStatusEnum status) {
        this.status = status;
    }

    public String getKommentar() {
        return kommentar;
    }

    public void setKommentar(String kommentar) {
        this.kommentar = kommentar;
    }

    public String getGeprueftVon() {
        return geprueftVon;
    }

    public void setGeprueftVon(String geprueftVon) {
        this.geprueftVon = geprueftVon;
    }

    public LocalDateTime getEingereichtAm() {
        return eingereichtAm;
    }

    public void setEingereichtAm(LocalDateTime eingereichtAm) {
        this.eingereichtAm = eingereichtAm;
    }

    public LocalDateTime getGeprueftAm() {
        return geprueftAm;
    }

    public void setGeprueftAm(LocalDateTime geprueftAm) {
        this.geprueftAm = geprueftAm;
    }
}
