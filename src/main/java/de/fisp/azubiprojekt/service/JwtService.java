package de.fisp.azubiprojekt.service;

import de.fisp.azubiprojekt.entity.UserAuthentication;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.JwtException;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import javax.crypto.SecretKey;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Duration;
import java.time.Instant;
import java.util.Date;
import java.util.Optional;

/**
 * Erstellt und prueft JSON Web Tokens (HS256).
 * Das Token enthaelt Benutzername, Rolle und eine Token-Version. Aendert sich das Passwort,
 * wird die Version erhoeht und alle alten Tokens werden ungueltig.
 */
@Service
public class JwtService {

    public static final String CLAIM_ROLE = "role";
    public static final String CLAIM_TOKEN_VERSION = "tv";

    private final SecretKey key;
    private final Duration validity;

    /** Standardwert aus application.properties – nur fuer die lokale Entwicklung. */
    static final String DEV_SECRET = "lokaler-entwicklungs-schluessel-bitte-in-produktion-ersetzen";

    public JwtService(@Value("${app.jwt.secret}") String secret,
                      @Value("${app.jwt.expiration-hours:12}") long expirationHours,
                      @Value("${app.jwt.require-secret:false}") boolean requireSecret) {
        if (requireSecret && (secret == null || secret.equals(DEV_SECRET) || secret.length() < 32)) {
            throw new IllegalStateException("Für den Serverbetrieb muss JWT_SECRET gesetzt sein "
                    + "(mindestens 32 zufällige Zeichen). Das Installationsskript erzeugt ihn automatisch.");
        }
        this.key = Keys.hmacShaKeyFor(sha256(secret));
        this.validity = Duration.ofHours(expirationHours);
    }

    public String createToken(UserAuthentication user) {
        Instant now = Instant.now();
        return Jwts.builder()
                .subject(user.getUsername())
                .claim(CLAIM_ROLE, user.getRole().name())
                .claim(CLAIM_TOKEN_VERSION, user.getTokenVersion())
                .issuedAt(Date.from(now))
                .expiration(Date.from(now.plus(validity)))
                .signWith(key)
                .compact();
    }

    public Instant expiresAt(String token) {
        return parse(token).map(c -> c.getExpiration().toInstant()).orElse(Instant.now());
    }

    /** Liefert die Claims eines gueltigen Tokens, sonst {@link Optional#empty()}. */
    public Optional<Claims> parse(String token) {
        try {
            return Optional.of(Jwts.parser().verifyWith(key).build().parseSignedClaims(token).getPayload());
        } catch (JwtException | IllegalArgumentException e) {
            return Optional.empty();
        }
    }

    private static byte[] sha256(String value) {
        try {
            return MessageDigest.getInstance("SHA-256").digest(value.getBytes(StandardCharsets.UTF_8));
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException(e);
        }
    }
}
