package de.fisp.azubiprojekt.dto.response;

import de.fisp.azubiprojekt.entity.Person;
import de.fisp.azubiprojekt.entity.Users;
import de.fisp.azubiprojekt.util.RoleEnum;

import java.time.LocalDate;
import java.time.LocalDateTime;

public record PersonResponseDTO(
        Long id,
        String firstName,
        String lastName,
        String middleName,
        String fullName,
        RoleEnum role,
        String roleLabel,
        String username,
        String beruf,
        int yearOfTraining,
        LocalDate dateOfTrainingStart,
        LocalDate dateOfBirth,
        LocalDateTime lastLogin) {

    public static PersonResponseDTO from(Person person) {
        Users users = person.getUsers();
        return new PersonResponseDTO(
                person.getId(),
                person.getFirstName(),
                person.getLastName(),
                person.getMiddleName(),
                person.getFullName(),
                person.getRole(),
                person.getRole().getLabel(),
                users != null ? users.getUsername() : null,
                person.getBeruf(),
                person.getYearOfTraining(),
                person.getDateOfTrainingStart(),
                person.getDateOfBirth(),
                users != null ? users.getLastSuccessfulLogin() : null);
    }
}
