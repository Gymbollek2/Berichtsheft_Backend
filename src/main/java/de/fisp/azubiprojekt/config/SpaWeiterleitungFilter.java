package de.fisp.azubiprojekt.config;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.core.io.ResourceLoader;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.List;

/**
 * Liefert das Angular-Frontend direkt aus dem Backend aus (ein Server, eine Adresse).
 * <p>
 * Die gebauten Dateien liegen im Ordner {@code ./frontend} (siehe {@code spring.web.resources.static-locations})
 * und werden von Spring als statische Dateien ausgeliefert. Dieser Filter sorgt dafuer, dass auch
 * Seiten-Adressen der Single-Page-App wie {@code /berichtsheft?datum=…} beim Neuladen funktionieren:
 * Solche Aufrufe (GET, kein API-Pfad, keine Dateiendung) bekommen die {@code index.html}, das Routing
 * uebernimmt dann Angular. Ohne gebautes Frontend (lokale Entwicklung mit {@code ng serve}) passiert nichts.
 */
@Component
@Order(Ordered.HIGHEST_PRECEDENCE + 10)
public class SpaWeiterleitungFilter extends OncePerRequestFilter {

    private static final List<String> BACKEND_PFADE = List.of("/v1/", "/h2-console", "/error");

    private final ResourceLoader resourceLoader;
    private final String[] staticLocations;
    private volatile boolean indexGefunden;

    public SpaWeiterleitungFilter(ResourceLoader resourceLoader,
                                  @Value("${spring.web.resources.static-locations:classpath:/static/}") String[] staticLocations) {
        this.resourceLoader = resourceLoader;
        this.staticLocations = staticLocations;
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
            throws ServletException, IOException {
        if (istSeitenAufruf(request) && frontendVorhanden()) {
            request.getRequestDispatcher("/index.html").forward(request, response);
            return;
        }
        chain.doFilter(request, response);
    }

    /** GET/HEAD auf eine Seite der App: kein API-Pfad und keine Datei (letzter Pfadteil ohne Punkt). */
    static boolean istSeitenAufruf(HttpServletRequest request) {
        String method = request.getMethod();
        if (!"GET".equals(method) && !"HEAD".equals(method)) {
            return false;
        }
        String pfad = request.getRequestURI().substring(request.getContextPath().length());
        if (pfad.isEmpty() || "/".equals(pfad)) {
            return true;
        }
        if (BACKEND_PFADE.stream().anyMatch(pfad::startsWith)) {
            return false;
        }
        String letzterTeil = pfad.substring(pfad.lastIndexOf('/') + 1);
        return !letzterTeil.contains(".");
    }

    private boolean frontendVorhanden() {
        if (!indexGefunden) {
            for (String location : staticLocations) {
                String basis = location.trim().endsWith("/") ? location.trim() : location.trim() + "/";
                if (resourceLoader.getResource(basis + "index.html").exists()) {
                    indexGefunden = true;
                    break;
                }
            }
        }
        return indexGefunden;
    }
}
