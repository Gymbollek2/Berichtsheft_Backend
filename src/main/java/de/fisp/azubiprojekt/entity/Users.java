package de.fisp.azubiprojekt.entity;

import de.fisp.azubiprojekt.util.RoleEnum;
import jakarta.persistence.*;

import java.time.LocalDateTime;

/** Login-Konto einer {@link Person}. Die Rolle wird immer von der Person uebernommen. */
@Entity
@Table(name = "users")
public class Users {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, unique = true)
    private String username;

    /** BCrypt-Hash, niemals Klartext. */
    @Column(nullable = false)
    private String password;

    @OneToOne(optional = false)
    @JoinColumn(name = "person_id", nullable = false, unique = true)
    private Person person;

    private LocalDateTime lastSuccessfulLogin;

    /** Wird bei jeder Passwortaenderung erhoeht und macht damit alte Tokens ungueltig. */
    private int tokenVersion;

    public Users() {
    }

    public Users(String username, String password, Person person) {
        this.username = username;
        this.password = password;
        this.person = person;
    }

    public RoleEnum getRole() {
        return person.getRole();
    }

    public Long getId() {
        return id;
    }

    public String getUsername() {
        return username;
    }

    public void setUsername(String username) {
        this.username = username;
    }

    public String getPassword() {
        return password;
    }

    public void setPassword(String password) {
        this.password = password;
    }

    public Person getPerson() {
        return person;
    }

    public void setPerson(Person person) {
        this.person = person;
    }

    public LocalDateTime getLastSuccessfulLogin() {
        return lastSuccessfulLogin;
    }

    public void setLastSuccessfulLogin(LocalDateTime lastSuccessfulLogin) {
        this.lastSuccessfulLogin = lastSuccessfulLogin;
    }

    public int getTokenVersion() {
        return tokenVersion;
    }

    public void incrementTokenVersion() {
        this.tokenVersion++;
    }
}
