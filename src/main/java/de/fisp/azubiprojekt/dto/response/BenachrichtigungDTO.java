package de.fisp.azubiprojekt.dto.response;

import de.fisp.azubiprojekt.entity.Benachrichtigung;
import de.fisp.azubiprojekt.util.BenachrichtigungTypEnum;

import java.time.LocalDateTime;

public record BenachrichtigungDTO(
        Long id,
        BenachrichtigungTypEnum typ,
        String titel,
        String text,
        String link,
        LocalDateTime erstelltAm,
        boolean gelesen) {

    public static BenachrichtigungDTO from(Benachrichtigung b) {
        return new BenachrichtigungDTO(b.getId(), b.getTyp(), b.getTitel(), b.getText(), b.getLink(),
                b.getErstelltAm(), b.isGelesen());
    }
}
