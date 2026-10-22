package de.fisp.azubiprojekt.config;

import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class SpaWeiterleitungFilterTest {

    private static boolean seite(String method, String uri) {
        return SpaWeiterleitungFilter.istSeitenAufruf(new MockHttpServletRequest(method, uri));
    }

    @Test
    void seitenDerAppBekommenIndexHtml() {
        assertTrue(seite("GET", "/"));
        assertTrue(seite("GET", "/dashboard"));
        assertTrue(seite("GET", "/berichtsheft/azubis/12"));
        assertTrue(seite("HEAD", "/lernziel-view-all"));
    }

    @Test
    void apiDateienUndAndereMethodenNicht() {
        assertFalse(seite("GET", "/v1/berichtsheft/woche"));
        assertFalse(seite("GET", "/h2-console"));
        assertFalse(seite("GET", "/error"));
        assertFalse(seite("GET", "/index.html"));
        assertFalse(seite("GET", "/main-ABC123.js"));
        assertFalse(seite("GET", "/media/ibm-plex-sans-latin-400-normal.woff2"));
        assertFalse(seite("POST", "/dashboard"));
    }
}
