package de.fisp.azubiprojekt.dao;

import de.fisp.azubiprojekt.entity.Person;
import de.fisp.azubiprojekt.entity.Wochenbericht;
import de.fisp.azubiprojekt.util.BerichtStatusEnum;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

@Repository
public interface WochenberichtDAO extends JpaRepository<Wochenbericht, Long> {

    Optional<Wochenbericht> findByPersonAndWeekStart(Person person, LocalDate weekStart);

    List<Wochenbericht> findByPersonOrderByWeekStartDesc(Person person);

    List<Wochenbericht> findByStatusOrderByEingereichtAmAsc(BerichtStatusEnum status);

    long countByStatus(BerichtStatusEnum status);
}
