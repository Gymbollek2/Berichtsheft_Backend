package de.fisp.azubiprojekt.dao;

import de.fisp.azubiprojekt.entity.Lernziel;
import de.fisp.azubiprojekt.entity.Person;
import de.fisp.azubiprojekt.entity.PersonLernziel;
import de.fisp.azubiprojekt.util.StatusEnum;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface PersonLernzielDAO extends JpaRepository<PersonLernziel, Long> {

    List<PersonLernziel> findByPerson(Person person);

    List<PersonLernziel> findByPersonAndStatus(Person person, StatusEnum status);

    List<PersonLernziel> findByLernziel(Lernziel lernziel);

    Optional<PersonLernziel> findByPersonAndLernziel(Person person, Lernziel lernziel);

    boolean existsByPersonAndLernziel(Person person, Lernziel lernziel);
}
