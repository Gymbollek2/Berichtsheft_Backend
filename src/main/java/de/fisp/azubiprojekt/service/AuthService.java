package de.fisp.azubiprojekt.service;

import de.fisp.azubiprojekt.dao.UserDAO;
import de.fisp.azubiprojekt.dto.request.LoginRequestDTO;
import de.fisp.azubiprojekt.dto.request.PasswordChangeRequestDTO;
import de.fisp.azubiprojekt.dto.response.LoginResponseDTO;
import de.fisp.azubiprojekt.dto.response.PersonResponseDTO;
import de.fisp.azubiprojekt.entity.UserAuthentication;
import de.fisp.azubiprojekt.entity.Users;
import de.fisp.azubiprojekt.exceptions.ActionNotAllowedException;
import de.fisp.azubiprojekt.exceptions.InvalidRequestException;
import de.fisp.azubiprojekt.exceptions.ResourceNotFoundException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.Arrays;
import java.util.Locale;
import java.util.Set;
import java.util.stream.Collectors;

@Service
public class AuthService {

    public static final String NICHT_BERECHTIGT = "Sie sind nicht berechtigt, auf diese Anwendung zuzugreifen. "
            + "Sollte es sich um einen Fehler handeln, wenden Sie sich bitte an das Ausbilderteam.";

    private static final Logger log = LoggerFactory.getLogger(AuthService.class);

    private final AuthenticationManager authenticationManager;
    private final JwtService jwtService;
    private final UserDAO userDAO;
    private final PasswordEncoder passwordEncoder;
    private final CurrentUserService currentUserService;
    private final boolean passwortLogin;
    /** Erlaubte Windows-Domaenen (Kurzname, z. B. FIRMA) – leer = jede Domaene. */
    private final Set<String> ssoDomaenen;

    public AuthService(AuthenticationManager authenticationManager, JwtService jwtService, UserDAO userDAO,
                       PasswordEncoder passwordEncoder, CurrentUserService currentUserService,
                       @Value("${app.password-login.enabled:true}") boolean passwortLogin,
                       @Value("${app.sso.domains:}") String ssoDomaenen) {
        this.authenticationManager = authenticationManager;
        this.jwtService = jwtService;
        this.userDAO = userDAO;
        this.passwordEncoder = passwordEncoder;
        this.currentUserService = currentUserService;
        this.passwortLogin = passwortLogin;
        this.ssoDomaenen = Arrays.stream(ssoDomaenen.split(","))
                .map(d -> d.trim().toLowerCase(Locale.ROOT))
                .filter(d -> !d.isEmpty())
                .collect(Collectors.toUnmodifiableSet());
    }

    /** Prueft Benutzername/Passwort und liefert bei Erfolg ein JWT. Wirft sonst BadCredentialsException (401). */
    @Transactional
    public LoginResponseDTO login(LoginRequestDTO request) {
        if (!passwortLogin) {
            throw new ActionNotAllowedException("Die Anmeldung mit Passwort ist abgeschaltet. Bitte über Windows anmelden.");
        }
        Authentication auth = authenticationManager.authenticate(
                new UsernamePasswordAuthenticationToken(request.username().trim(), request.password()));
        UserAuthentication principal = (UserAuthentication) auth.getPrincipal();

        Users users = userDAO.findById(principal.getUserId())
                .orElseThrow(() -> new ResourceNotFoundException("Benutzer nicht gefunden"));
        return anmelden(users);
    }

    /**
     * Anmeldung mit dem Windows-Konto ({@code DOMAENE\benutzer} oder {@code benutzer@domaene}). Zugriff hat nur,
     * wer mit diesem Benutzernamen in der Personenliste steht – sonst ActionNotAllowedException (403).
     */
    @Transactional
    public LoginResponseDTO ssoLogin(String konto) {
        String domaene = "";
        String name = konto.trim();
        int backslash = name.indexOf('\\');
        int at = name.indexOf('@');
        if (backslash >= 0) {
            domaene = name.substring(0, backslash);
            name = name.substring(backslash + 1);
        } else if (at >= 0) {
            domaene = name.substring(at + 1);
            name = name.substring(0, at);
        }
        domaene = domaene.toLowerCase(Locale.ROOT);
        // Bei benutzer@firma.local zaehlt der erste Teil der DNS-Domaene als Kurzname
        String kurzname = domaene.contains(".") ? domaene.substring(0, domaene.indexOf('.')) : domaene;
        if (!ssoDomaenen.isEmpty() && !ssoDomaenen.contains(domaene) && !ssoDomaenen.contains(kurzname)) {
            log.info("Windows-Anmeldung abgelehnt: {} gehört zu keiner erlaubten Domäne", konto);
            throw new ActionNotAllowedException(NICHT_BERECHTIGT);
        }
        String benutzername = name.toLowerCase(Locale.ROOT);
        Users users = userDAO.findByUsernameIgnoreCase(benutzername).orElseThrow(() -> {
            log.info("Windows-Anmeldung abgelehnt: {} steht nicht in der Personenliste", konto);
            return new ActionNotAllowedException(NICHT_BERECHTIGT);
        });
        return anmelden(users);
    }

    private LoginResponseDTO anmelden(Users users) {
        users.setLastSuccessfulLogin(LocalDateTime.now());
        String token = jwtService.createToken(new UserAuthentication(users));
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
