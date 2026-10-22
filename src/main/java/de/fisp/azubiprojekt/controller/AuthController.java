package de.fisp.azubiprojekt.controller;

import de.fisp.azubiprojekt.dto.request.LoginRequestDTO;
import de.fisp.azubiprojekt.dto.request.PasswordChangeRequestDTO;
import de.fisp.azubiprojekt.dto.response.AnmeldeOptionenDTO;
import de.fisp.azubiprojekt.dto.response.LoginResponseDTO;
import de.fisp.azubiprojekt.dto.response.PersonResponseDTO;
import de.fisp.azubiprojekt.exceptions.ActionNotAllowedException;
import de.fisp.azubiprojekt.exceptions.ErrorResponse;
import de.fisp.azubiprojekt.service.AuthService;
import de.fisp.azubiprojekt.service.WindowsAnmeldung;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDateTime;
import java.util.Base64;
import java.util.LinkedHashMap;
import java.util.Map;

/** Anmeldung und eigenes Konto. */
@RestController
@RequestMapping("/v1/passport")
public class AuthController {

    private static final Logger log = LoggerFactory.getLogger(AuthController.class);
    private static final String NEGOTIATE = "Negotiate";

    private final AuthService authService;
    private final ObjectProvider<WindowsAnmeldung> windowsAnmeldung;
    private final boolean sso;
    private final boolean passwortLogin;

    public AuthController(AuthService authService, ObjectProvider<WindowsAnmeldung> windowsAnmeldung,
                          @Value("${app.sso.enabled:false}") boolean sso,
                          @Value("${app.password-login.enabled:true}") boolean passwortLogin) {
        this.authService = authService;
        this.windowsAnmeldung = windowsAnmeldung;
        this.sso = sso;
        this.passwortLogin = passwortLogin;
    }

    /** Welche Anmeldearten das Frontend anbieten soll (ohne Anmeldung abrufbar). */
    @GetMapping("/optionen")
    public AnmeldeOptionenDTO optionen() {
        return new AnmeldeOptionenDTO(sso, passwortLogin);
    }

    @PostMapping("/login")
    public LoginResponseDTO login(@Valid @RequestBody LoginRequestDTO request) {
        return authService.login(request);
    }

    /**
     * Windows-Anmeldung (Single Sign-on). Ablauf: Ohne Windows-Token antwortet der Server mit 401 und
     * {@code WWW-Authenticate: Negotiate}; der Browser schickt daraufhin automatisch das Windows-Konto mit.
     * <ul>
     *   <li>200 – Konto steht in der Personenliste, Antwort wie beim Login (Token)</li>
     *   <li>403 – Konto ist bekannt, aber nicht in der Personenliste ({@code konto} in der Antwort)</li>
     *   <li>401 ohne {@code WWW-Authenticate} – der Browser konnte kein Windows-Konto liefern</li>
     * </ul>
     */
    @GetMapping("/sso")
    public ResponseEntity<?> sso(@RequestHeader(value = HttpHeaders.AUTHORIZATION, required = false) String authorization,
                                 HttpServletRequest request) {
        WindowsAnmeldung anmeldung = sso ? windowsAnmeldung.getIfAvailable() : null;
        if (anmeldung == null) {
            return fehler(HttpStatus.NOT_FOUND, "Die Windows-Anmeldung ist nicht eingeschaltet.", null);
        }
        String[] anfrage = tokenAus(authorization);
        if (anfrage == null) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                    .header(HttpHeaders.WWW_AUTHENTICATE, NEGOTIATE)
                    .body(ErrorResponse.of(401, "Windows-Anmeldung erforderlich"));
        }

        String verbindung = request.getRemoteAddr() + ":" + request.getRemotePort();
        WindowsAnmeldung.Schritt schritt;
        try {
            schritt = anmeldung.pruefe(verbindung, Base64.getDecoder().decode(anfrage[1]), anfrage[0]);
        } catch (RuntimeException e) {
            anmeldung.abbrechen(verbindung);
            log.warn("Windows-Anmeldung fehlgeschlagen ({}): {}", verbindung, e.getMessage());
            // Bewusst ohne WWW-Authenticate, sonst versucht es der Browser endlos erneut
            return fehler(HttpStatus.UNAUTHORIZED, "Die Windows-Anmeldung ist fehlgeschlagen.", null);
        }

        String antwort = schritt.antwortToken() == null || schritt.antwortToken().length == 0
                ? null : anfrage[0] + " " + Base64.getEncoder().encodeToString(schritt.antwortToken());
        if (!schritt.fertig()) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                    .header(HttpHeaders.WWW_AUTHENTICATE, antwort == null ? anfrage[0] : antwort)
                    .body(ErrorResponse.of(401, "Windows-Anmeldung läuft"));
        }
        if (schritt.konto() == null) {
            return fehler(HttpStatus.FORBIDDEN, AuthService.NICHT_BERECHTIGT, null);
        }
        try {
            LoginResponseDTO login = authService.ssoLogin(schritt.konto());
            ResponseEntity.BodyBuilder ok = ResponseEntity.ok();
            if (antwort != null) {
                ok.header(HttpHeaders.WWW_AUTHENTICATE, antwort);
            }
            return ok.body(login);
        } catch (ActionNotAllowedException e) {
            return fehler(HttpStatus.FORBIDDEN, e.getMessage(), schritt.konto());
        }
    }

    @GetMapping("/me")
    public PersonResponseDTO me() {
        return authService.me();
    }

    @PostMapping("/change-password")
    public LoginResponseDTO changePassword(@Valid @RequestBody PasswordChangeRequestDTO request) {
        return authService.changePassword(request);
    }

    /**
     * Verfahren und Token aus "Negotiate ..." bzw. "NTLM ..." (Clients ohne Kerberos), sonst null.
     * Die Antwort muss dasselbe Verfahren nennen, sonst bricht der Client den Handshake ab.
     */
    private static String[] tokenAus(String authorization) {
        if (authorization == null) {
            return null;
        }
        for (String schema : new String[]{NEGOTIATE, "NTLM"}) {
            if (authorization.regionMatches(true, 0, schema + " ", 0, schema.length() + 1)) {
                String token = authorization.substring(schema.length() + 1).trim();
                return token.isEmpty() ? null : new String[]{schema, token};
            }
        }
        return null;
    }

    /** Fehlerantwort im ueblichen Format, bei 403 mit dem Windows-Konto, damit es dem Ausbilderteam genannt werden kann. */
    private static ResponseEntity<Map<String, Object>> fehler(HttpStatus status, String message, String konto) {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("status", status.value());
        body.put("message", message);
        body.put("timestamp", LocalDateTime.now());
        if (konto != null) {
            body.put("konto", konto);
        }
        return ResponseEntity.status(status).body(body);
    }
}
