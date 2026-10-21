package de.fisp.azubiprojekt.dao;

import de.fisp.azubiprojekt.entity.Berichtsheft;
import de.fisp.azubiprojekt.entity.Lernziel;
import de.fisp.azubiprojekt.entity.Person;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

@Repository
public interface BerichtsheftDAO extends JpaRepository<Berichtsheft, Long> {

    Optional<Berichtsheft> findByPersonAndDate(Person person, LocalDate date);

    List<Berichtsheft> findByPersonAndDateBetweenOrderByDateAsc(Person person, LocalDate from, LocalDate to);

    List<Berichtsheft> findByQualificationsContains(Lernziel lernziel);
}
