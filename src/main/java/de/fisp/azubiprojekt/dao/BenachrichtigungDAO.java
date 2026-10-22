package de.fisp.azubiprojekt.dao;

import de.fisp.azubiprojekt.entity.Benachrichtigung;
import de.fisp.azubiprojekt.entity.Person;
import de.fisp.azubiprojekt.util.BenachrichtigungTypEnum;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface BenachrichtigungDAO extends JpaRepository<Benachrichtigung, Long> {

    List<Benachrichtigung> findByEmpfaengerOrderByErstelltAmDescIdDesc(Person empfaenger, Pageable pageable);

    long countByEmpfaengerAndGelesenFalse(Person empfaenger);

    boolean existsByEmpfaengerAndBezug(Person empfaenger, String bezug);

    @Modifying
    @Query("update Benachrichtigung b set b.gelesen = true where b.empfaenger = :empfaenger and b.gelesen = false")
    int markiereAlleGelesen(@Param("empfaenger") Person empfaenger);

    /** Markiert die Nachrichten zu einem erledigten Anlass als gelesen – bei allen Empfaengern. */
    @Modifying
    @Query("update Benachrichtigung b set b.gelesen = true where b.bezug = :bezug and b.typ = :typ and b.gelesen = false")
    int markiereErledigt(@Param("bezug") String bezug, @Param("typ") BenachrichtigungTypEnum typ);

    /** Wie {@link #markiereErledigt}, aber nur fuer einen Empfaenger. */
    @Modifying
    @Query("update Benachrichtigung b set b.gelesen = true "
            + "where b.empfaenger = :empfaenger and b.bezug = :bezug and b.typ = :typ and b.gelesen = false")
    int markiereErledigt(@Param("empfaenger") Person empfaenger, @Param("bezug") String bezug,
                         @Param("typ") BenachrichtigungTypEnum typ);
}
