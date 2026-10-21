package de.fisp.azubiprojekt.entity;

import de.fisp.azubiprojekt.util.RoleEnum;
import jakarta.persistence.*;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

/** Fachliche Daten einer Person (Azubi, Ausbilder, Admin). Login-Daten liegen in {@link Users}. */
@Entity
@Table(name = "person")
public class Person {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    private String firstName;

    @Column(nullable = false)
    private String lastName;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private RoleEnum role;

    /** Ausbildungsberuf, z. B. "Fachinformatiker/in Anwendungsentwicklung". */
    private String beruf;

    private int yearOfTraining;
    private LocalDate dateOfTrainingStart;
    private LocalDate dateOfBirth;

    @OneToOne(mappedBy = "person", cascade = CascadeType.ALL, orphanRemoval = true)
    private Users users;

    @OneToMany(mappedBy = "person", cascade = CascadeType.ALL, orphanRemoval = true)
    private List<PersonLernziel> personLernziele = new ArrayList<>();

    @OneToMany(mappedBy = "person", cascade = CascadeType.ALL, orphanRemoval = true)
    private List<Berichtsheft> berichtshefte = new ArrayList<>();

    @OneToMany(mappedBy = "person", cascade = CascadeType.ALL, orphanRemoval = true)
    private List<Wochenbericht> wochenberichte = new ArrayList<>();

    public Person() {
    }

    public Person(String firstName, String lastName, RoleEnum role, int yearOfTraining,
                  LocalDate dateOfTrainingStart, LocalDate dateOfBirth) {
        this.firstName = firstName;
        this.lastName = lastName;
        this.role = role;
        this.yearOfTraining = yearOfTraining;
        this.dateOfTrainingStart = dateOfTrainingStart;
        this.dateOfBirth = dateOfBirth;
    }

    public String getFullName() {
        return firstName + " " + lastName;
    }

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public String getFirstName() {
        return firstName;
    }

    public void setFirstName(String firstName) {
        this.firstName = firstName;
    }

    public String getLastName() {
        return lastName;
    }

    public void setLastName(String lastName) {
        this.lastName = lastName;
    }

    public RoleEnum getRole() {
        return role;
    }

    public void setRole(RoleEnum role) {
        this.role = role;
    }

    public String getBeruf() {
        return beruf;
    }

    public void setBeruf(String beruf) {
        this.beruf = beruf;
    }

    public int getYearOfTraining() {
        return yearOfTraining;
    }

    public void setYearOfTraining(int yearOfTraining) {
        this.yearOfTraining = yearOfTraining;
    }

    public LocalDate getDateOfTrainingStart() {
        return dateOfTrainingStart;
    }

    public void setDateOfTrainingStart(LocalDate dateOfTrainingStart) {
        this.dateOfTrainingStart = dateOfTrainingStart;
    }

    public LocalDate getDateOfBirth() {
        return dateOfBirth;
    }

    public void setDateOfBirth(LocalDate dateOfBirth) {
        this.dateOfBirth = dateOfBirth;
    }

    public Users getUsers() {
        return users;
    }

    public void setUsers(Users users) {
        this.users = users;
    }

    public List<PersonLernziel> getPersonLernziele() {
        return personLernziele;
    }

    public List<Berichtsheft> getBerichtshefte() {
        return berichtshefte;
    }

    public List<Wochenbericht> getWochenberichte() {
        return wochenberichte;
    }
}
