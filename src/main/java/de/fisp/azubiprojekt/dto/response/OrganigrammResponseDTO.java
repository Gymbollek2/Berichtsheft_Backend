package de.fisp.azubiprojekt.dto.response;

import de.fisp.azubiprojekt.entity.Person;
import de.fisp.azubiprojekt.util.RoleEnum;

import java.util.List;

/**
 * Ausbildungsorganigramm: oben die Ausbilder, darunter die Lernenden nach Ausbildungsjahr (hoechstes zuerst).
 * Enthaelt bewusst nur, was jeder sehen darf – keine Benutzernamen, Geburts- oder Anmeldedaten.
 */
public record OrganigrammResponseDTO(List<Mitglied> ausbilder, List<Jahrgang> jahrgaenge) {

    /** Alle Lernenden eines Ausbildungsjahres. */
    public record Jahrgang(int ausbildungsjahr, List<Mitglied> mitglieder) {
    }

    public record Mitglied(Long id, String firstName, String lastName, String fullName, RoleEnum role, String roleLabel,
                           String beruf) {

        public static Mitglied from(Person person) {
            return new Mitglied(person.getId(), person.getFirstName(), person.getLastName(), person.getFullName(),
                    person.getRole(), person.getRoleLabel(), person.getBeruf());
        }
    }
}
