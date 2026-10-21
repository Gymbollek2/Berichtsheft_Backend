package de.fisp.azubiprojekt.service;

import de.fisp.azubiprojekt.dao.UserDAO;
import de.fisp.azubiprojekt.dto.request.LoginRequestDTO;
import de.fisp.azubiprojekt.dto.request.PasswordChangeRequestDTO;
import de.fisp.azubiprojekt.dto.response.LoginResponseDTO;
import de.fisp.azubiprojekt.dto.response.PersonResponseDTO;
import de.fisp.azubiprojekt.entity.UserAuthentication;
import de.fisp.azubiprojekt.entity.Users;
import de.fisp.azubiprojekt.exceptions.InvalidRequestException;
import de.fisp.azubiprojekt.exceptions.ResourceNotFoundException;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;

@Service
public class AuthService {

    private final AuthenticationManager authenticationManager;
    private final JwtService jwtService;
    private final UserDAO userDAO;
    private final PasswordEncoder passwordEncoder;
    private final CurrentUserService currentUserService;

    public AuthService(AuthenticationManager authenticationManager, JwtService jwtService, UserDAO userDAO,
                       PasswordEncoder passwordEncoder, CurrentUserService currentUserService) {
        this.authenticationManager = authenticationManager;
        this.jwtService = jwtService;
        this.userDAO = userDAO;
        this.passwordEncoder = passwordEncoder;
        this.currentUserService = currentUserService;
    }

    /** Prueft Benutzername/Passwort und liefert bei Erfolg ein JWT. Wirft sonst BadCredentialsException (401). */
    @Transactional
    public LoginResponseDTO login(LoginRequestDTO request) {
        Authentication auth = authenticationManager.authenticate(
                new UsernamePasswordAuthenticationToken(request.username().trim(), request.password()));
        UserAuthentication principal = (UserAuthentication) auth.getPrincipal();

        Users users = userDAO.findById(principal.getUserId())
                .orElseThrow(() -> new ResourceNotFoundException("Benutzer nicht gefunden"));
        users.setLastSuccessfulLogin(LocalDateTime.now());

        String token = jwtService.createToken(principal);
        return new LoginResponseDTO(token, jwtService.expiresAt(token), PersonResponseDTO.from(users.getPerson()));
    }

    @Transactional(readOnly = true)
    public PersonResponseDTO me() {
        return PersonResponseDTO.from(currentUserService.getPerson());
    }

    /** Aendert das eigene Passwort. Alle bisher ausgestellten Tokens werden dadurch ungueltig. */
    @Transactional
    public LoginResponseDTO changePassword(PasswordChangeRequestDTO request) {
        Users users = userDAO.findById(currentUserService.getPrincipal().getUserId())
                .orElseThrow(() -> new ResourceNotFoundException("Benutzer nicht gefunden"));
        if (!passwordEncoder.matches(request.oldPassword(), users.getPassword())) {
            throw new InvalidRequestException("Das aktuelle Passwort ist falsch");
        }
        users.setPassword(passwordEncoder.encode(request.newPassword()));
        users.incrementTokenVersion();
        userDAO.save(users);

        // Neues Token ausstellen, damit der Benutzer angemeldet bleibt
        String token = jwtService.createToken(new UserAuthentication(users));
        return new LoginResponseDTO(token, jwtService.expiresAt(token), PersonResponseDTO.from(users.getPerson()));
    }
}
