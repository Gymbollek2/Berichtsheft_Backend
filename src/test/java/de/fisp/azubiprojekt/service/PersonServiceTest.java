package de.fisp.azubiprojekt.service;

import de.fisp.azubiprojekt.dao.LernzielDAO;
import de.fisp.azubiprojekt.dao.PersonDAO;
import de.fisp.azubiprojekt.dao.UserDAO;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.crypto.password.PasswordEncoder;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class PersonServiceTest {

    @Mock
    private PersonDAO personDAO;
    @Mock
    private UserDAO userDAO;
    @Mock
    private PasswordEncoder passwordEncoder;
    @Mock
    private CurrentUserService currentUserService;
    @Mock
    private PersonLernzielService personLernzielService;
    @Mock
    private LernzielDAO lernzielDAO;

    @InjectMocks
    private PersonService personService;

    @Test
    void generatesUsernameWithoutUmlauts() {
        when(userDAO.existsByUsernameIgnoreCase("juergen.mueller-luedenscheidt")).thenReturn(false);
        assertEquals("juergen.mueller-luedenscheidt", personService.generateUsername("Jürgen", "Müller Lüdenscheidt"));
    }

    @Test
    void learnersNeedTrainingStartBeforeBirthIsNot() {
        org.junit.jupiter.api.Assertions.assertThrows(de.fisp.azubiprojekt.exceptions.InvalidRequestException.class,
                () -> personService.validateTrainingData(de.fisp.azubiprojekt.util.RoleEnum.ROLE_TRAINEE, null, null));
        org.junit.jupiter.api.Assertions.assertThrows(de.fisp.azubiprojekt.exceptions.InvalidRequestException.class,
                () -> personService.validateTrainingData(de.fisp.azubiprojekt.util.RoleEnum.ROLE_TRAINEE,
                        java.time.LocalDate.of(2024, 9, 1), java.time.LocalDate.of(2025, 1, 1)));
        // Ausbilder brauchen keinen Ausbildungsbeginn
        personService.validateTrainingData(de.fisp.azubiprojekt.util.RoleEnum.ROLE_TRAINER, null, null);
    }

    @Test
    void appendsNumberIfUsernameIsTaken() {
        when(userDAO.existsByUsernameIgnoreCase("max.mustermann")).thenReturn(true);
        when(userDAO.existsByUsernameIgnoreCase("max.mustermann2")).thenReturn(false);
        assertEquals("max.mustermann2", personService.generateUsername("Max", "Mustermann"));
    }
}
