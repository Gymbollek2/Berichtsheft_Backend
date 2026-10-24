package de.fisp.azubiprojekt.service;

import de.fisp.azubiprojekt.config.EmailEigenschaften;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.http.MediaType;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientResponseException;

import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Versand ueber Microsoft 365 per Microsoft Graph ({@code POST /users/{postfach}/sendMail}).
 * Die Anwendung meldet sich selbst per OAuth2 an (Client-Credentials-Flow mit Client-Secret einer
 * App-Registrierung in Microsoft Entra ID) – ohne Benutzerpasswort. Das Token wird bis kurz vor Ablauf wiederverwendet.
 * Benoetigt die Anwendungsberechtigung {@code Mail.Send} (siehe deploy/SERVER-ANLEITUNG.md).
 */
public class GraphEmailVersand implements EmailVersand {

    /** So lange vor Ablauf wird ein neues Token geholt. */
    private static final long PUFFER_SEKUNDEN = 120;
    private static final Pattern FEHLERTEXT = Pattern.compile("\"(?:error_description|message)\"\\s*:\\s*\"((?:[^\"\\\\]|\\\\.)*)\"");

    private final EmailEigenschaften eigenschaften;
    private final EmailEigenschaften.Graph graph;
    private final RestClient rest;

    private String token;
    private Instant tokenGueltigBis = Instant.EPOCH;

    public GraphEmailVersand(EmailEigenschaften eigenschaften, RestClient.Builder restClient) {
        this.eigenschaften = eigenschaften;
        this.graph = eigenschaften.graph();
        this.rest = restClient.build();
        if (eigenschaften.enabled()) {
            pruefeEinstellungen();
        }
    }

    @Override
    public String name() {
        return "Microsoft Graph";
    }

    @Override
    public void senden(VorbereiteteEmail email) {
        Map<String, Object> anfrage = anfrage(email);
        try {
            sendMail(token(), anfrage);
        } catch (RestClientResponseException e) {
            if (e.getStatusCode().value() != 401) {
                throw fehler("Microsoft Graph hat die Mail abgelehnt", e);
            }
            // Token wurde z. B. nach einer Rechteaenderung ungueltig: einmal mit neuem Token versuchen
            tokenVerwerfen();
            try {
                sendMail(token(), anfrage);
            } catch (RestClientResponseException erneut) {
                throw fehler("Microsoft Graph hat die Mail abgelehnt", erneut);
            }
        }
    }

    /** Postfach, aus dem gesendet wird – standardmaessig die Absenderadresse. */
    String postfach() {
        return graph.postfach() == null || graph.postfach().isBlank() ? eigenschaften.absender() : graph.postfach().trim();
    }

    private void sendMail(String accessToken, Map<String, Object> anfrage) {
        rest.post()
                .uri(ohneSchraegstrich(graph.apiUrl()) + "/users/{postfach}/sendMail", postfach())
                .headers(h -> h.setBearerAuth(accessToken))
                .contentType(MediaType.APPLICATION_JSON)
                .body(anfrage)
                .retrieve()
                .toBodilessEntity();
    }

    /** JSON fuer sendMail: https://learn.microsoft.com/graph/api/user-sendmail */
    Map<String, Object> anfrage(VorbereiteteEmail email) {
        Map<String, Object> nachricht = new LinkedHashMap<>();
        nachricht.put("subject", email.betreff());
        nachricht.put("body", Map.of("contentType", "HTML", "content", email.html()));
        nachricht.put("toRecipients", List.of(adresse(email.an(), email.anName())));
        if (eigenschaften.antwortAn() != null && !eigenschaften.antwortAn().isBlank()) {
            nachricht.put("replyTo", List.of(adresse(eigenschaften.antwortAn().trim(), null)));
        }
        List<Map<String, String>> kopfzeilen = new ArrayList<>();
        if (email.istUmgeleitet()) {
            // Eigene Kopfzeilen muessen bei Graph mit "X-" beginnen
            kopfzeilen.add(Map.of("name", "X-Original-To", "value", email.urspruenglichAn()));
        }
        if (!kopfzeilen.isEmpty()) {
            nachricht.put("internetMessageHeaders", kopfzeilen);
        }
        return Map.of("message", nachricht, "saveToSentItems", graph.imGesendetSpeichern());
    }

    private static Map<String, Object> adresse(String adresse, String name) {
        Map<String, String> emailAddress = new LinkedHashMap<>();
        emailAddress.put("address", adresse);
        if (name != null && !name.isBlank()) {
            emailAddress.put("name", name);
        }
        return Map.of("emailAddress", emailAddress);
    }

    // ------------------------------------------------------------------ OAuth2

    /** Access-Token per Client-Credentials-Flow, wird bis kurz vor Ablauf zwischengespeichert. */
    synchronized String token() {
        if (token != null && Instant.now().isBefore(tokenGueltigBis)) {
            return token;
        }
        MultiValueMap<String, String> formular = new LinkedMultiValueMap<>();
        formular.add("grant_type", "client_credentials");
        formular.add("client_id", graph.clientId().trim());
        formular.add("client_secret", graph.clientSecret());
        formular.add("scope", graph.scope());
        Map<String, Object> antwort;
        try {
            antwort = rest.post()
                    .uri(ohneSchraegstrich(graph.loginUrl()) + "/{tenant}/oauth2/v2.0/token", graph.tenantId().trim())
                    .contentType(MediaType.APPLICATION_FORM_URLENCODED)
                    .body(formular)
                    .retrieve()
                    .body(new ParameterizedTypeReference<Map<String, Object>>() {
                    });
        } catch (RestClientResponseException e) {
            throw fehler("Anmeldung bei Microsoft Entra ID fehlgeschlagen", e);
        }
        if (antwort == null || !(antwort.get("access_token") instanceof String neu)) {
            throw new IllegalStateException("Anmeldung bei Microsoft Entra ID: Antwort ohne access_token");
        }
        long gueltigSekunden = antwort.get("expires_in") instanceof Number n ? n.longValue()
                : Long.parseLong(String.valueOf(antwort.getOrDefault("expires_in", "300")));
        token = neu;
        tokenGueltigBis = Instant.now().plusSeconds(Math.max(0, gueltigSekunden - PUFFER_SEKUNDEN));
        return token;
    }

    private synchronized void tokenVerwerfen() {
        token = null;
        tokenGueltigBis = Instant.EPOCH;
    }

    // ------------------------------------------------------------------ Hilfsmethoden

    private void pruefeEinstellungen() {
        List<String> fehlend = new ArrayList<>();
        if (leer(graph.tenantId())) {
            fehlend.add("app.mail.graph.tenant-id (MAIL_GRAPH_TENANT_ID)");
        }
        if (leer(graph.clientId())) {
            fehlend.add("app.mail.graph.client-id (MAIL_GRAPH_CLIENT_ID)");
        }
        if (leer(graph.clientSecret())) {
            fehlend.add("app.mail.graph.client-secret (MAIL_GRAPH_CLIENT_SECRET)");
        }
        if (leer(postfach())) {
            fehlend.add("app.mail.graph.postfach oder app.mail.absender");
        }
        if (!fehlend.isEmpty()) {
            throw new IllegalStateException("E-Mail-Versand per Microsoft Graph (app.mail.versandart=graph) ist eingeschaltet, "
                    + "aber es fehlt: " + String.join(", ", fehlend)
                    + ". Werte setzen oder mit MAIL_VERSANDART=smtp bzw. MAIL_ENABLED=false starten.");
        }
    }

    /** Fehlermeldung inkl. Text von Microsoft (z. B. "AADSTS7000215: Invalid client secret provided"). */
    private static IllegalStateException fehler(String was, RestClientResponseException e) {
        Matcher m = FEHLERTEXT.matcher(e.getResponseBodyAsString());
        String text = m.find() ? m.group(1).replace("\\r", " ").replace("\\n", " ").replace("\\\"", "\"") : e.getStatusText();
        return new IllegalStateException(was + " (HTTP " + e.getStatusCode().value() + "): " + text, e);
    }

    private static boolean leer(String s) {
        return s == null || s.isBlank();
    }

    private static String ohneSchraegstrich(String url) {
        return url.replaceAll("/+$", "");
    }
}
