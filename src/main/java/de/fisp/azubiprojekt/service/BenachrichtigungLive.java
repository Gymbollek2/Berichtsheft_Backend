package de.fisp.azubiprojekt.service;

import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.io.IOException;
import java.time.Duration;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Live-Verbindungen der Glocke (Server-Sent Events): Jeder angemeldete Browser-Tab haelt eine offene Verbindung.
 * Aendern sich die Benachrichtigungen einer Person, bekommt sie sofort ein Ereignis und laedt ihre Liste neu –
 * ohne Neuladen der Seite und ohne auf die naechste regelmaessige Abfrage zu warten.
 */
@Component
public class BenachrichtigungLive {

    /** Ereignisname, auf den das Frontend hoert. */
    static final String EREIGNIS = "benachrichtigung";
    /** Danach baut der Browser die Verbindung von selbst neu auf (z. B. falls sich das Token geaendert hat). */
    private static final Duration LAUFZEIT = Duration.ofMinutes(30);

    private final Map<Long, Set<SseEmitter>> verbindungen = new ConcurrentHashMap<>();

    public SseEmitter verbinden(Long personId) {
        SseEmitter emitter = new SseEmitter(LAUFZEIT.toMillis());
        verbindungen.computeIfAbsent(personId, id -> ConcurrentHashMap.newKeySet()).add(emitter);
        Runnable entfernen = () -> entfernen(personId, emitter);
        emitter.onCompletion(entfernen);
        emitter.onTimeout(entfernen);
        emitter.onError(fehler -> entfernen.run());
        // Erstes Ereignis sofort, damit Browser und Proxys die Verbindung als offen erkennen
        senden(personId, emitter, SseEmitter.event().name("verbunden").data(""));
        return emitter;
    }

    /** Die Benachrichtigungen dieser Person haben sich geaendert – nach erfolgreichem Speichern melden. */
    public void geaendert(Long personId) {
        nachCommit(() -> melden(personId));
    }

    /** Aenderung, die mehrere (nicht einzeln bekannte) Personen betreffen kann. */
    public void alleGeaendert() {
        nachCommit(() -> verbindungen.keySet().forEach(this::melden));
    }

    int anzahlVerbindungen(Long personId) {
        return verbindungen.getOrDefault(personId, Set.of()).size();
    }

    /** Haelt die Verbindungen offen und raeumt abgebrochene auf (Proxys trennen stille Verbindungen sonst). */
    @Scheduled(fixedRate = 25_000)
    void lebenszeichen() {
        verbindungen.forEach((personId, emitters) ->
                emitters.forEach(emitter -> senden(personId, emitter, SseEmitter.event().comment("ping"))));
    }

    private void melden(Long personId) {
        verbindungen.getOrDefault(personId, Set.of())
                .forEach(emitter -> senden(personId, emitter, SseEmitter.event().name(EREIGNIS).data("")));
    }

    private void senden(Long personId, SseEmitter emitter, SseEmitter.SseEventBuilder ereignis) {
        try {
            emitter.send(ereignis);
        } catch (IOException | IllegalStateException e) {
            // Browser-Tab geschlossen oder Verbindung abgebrochen. Spring beendet die Anfrage dann selbst –
            // ein completeWithError wuerde den Abbruch nur erneut als Fehler durch die Fehlerbehandlung schicken.
            entfernen(personId, emitter);
        }
    }

    private void entfernen(Long personId, SseEmitter emitter) {
        verbindungen.computeIfPresent(personId, (id, emitters) -> {
            emitters.remove(emitter);
            return emitters.isEmpty() ? null : emitters;
        });
    }

    /** Erst melden, wenn die Aenderung gespeichert ist – sonst laedt der Browser noch den alten Stand. */
    private static void nachCommit(Runnable aktion) {
        if (!TransactionSynchronizationManager.isSynchronizationActive()) {
            aktion.run();
            return;
        }
        TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
            @Override
            public void afterCommit() {
                aktion.run();
            }
        });
    }
}
