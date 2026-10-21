package de.fisp.azubiprojekt.entity;

import de.fisp.azubiprojekt.util.RoleEnum;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.userdetails.UserDetails;

import java.util.Collection;
import java.util.List;

/**
 * Unveraenderliche Momentaufnahme eines {@link Users} fuer Spring Security.
 * Haelt bewusst keine JPA-Entity, damit sie ausserhalb von Transaktionen sicher genutzt werden kann.
 */
public class UserAuthentication implements UserDetails {

    private final Long userId;
    private final Long personId;
    private final String username;
    private final String password;
    private final RoleEnum role;
    private final int tokenVersion;

    public UserAuthentication(Users users) {
        this.userId = users.getId();
        this.personId = users.getPerson().getId();
        this.username = users.getUsername();
        this.password = users.getPassword();
        this.role = users.getRole();
        this.tokenVersion = users.getTokenVersion();
    }

    @Override
    public Collection<? extends GrantedAuthority> getAuthorities() {
        return List.of(new SimpleGrantedAuthority(role.name()));
    }

    @Override
    public String getPassword() {
        return password;
    }

    @Override
    public String getUsername() {
        return username;
    }

    public Long getUserId() {
        return userId;
    }

    public Long getPersonId() {
        return personId;
    }

    public RoleEnum getRole() {
        return role;
    }

    public int getTokenVersion() {
        return tokenVersion;
    }
}
