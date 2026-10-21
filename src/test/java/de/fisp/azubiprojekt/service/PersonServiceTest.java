package de.fisp.azubiprojekt.service;

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

    @InjectMocks
    private PersonService personService;

    @Test
    void generatesUsernameWithoutUmlauts() {
        when(userDAO.existsByUsernameIgnoreCase("juergen.mueller-luedenscheidt")).thenReturn(false);
        assertEquals("juergen.mueller-luedenscheidt", personService.generateUsername("Jürgen", "Müller Lüdenscheidt"));
    }

    @Test
    void appendsNumberIfUsernameIsTaken() {
        when(userDAO.existsByUsernameIgnoreCase("max.mustermann")).thenReturn(true);
        when(userDAO.existsByUsernameIgnoreCase("max.mustermann2")).thenReturn(false);
        assertEquals("max.mustermann2", personService.generateUsername("Max", "Mustermann"));
    }
}
