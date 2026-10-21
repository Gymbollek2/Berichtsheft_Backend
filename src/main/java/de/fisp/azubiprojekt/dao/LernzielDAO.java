package de.fisp.azubiprojekt.dao;

import de.fisp.azubiprojekt.entity.Lernziel;
import de.fisp.azubiprojekt.util.KindEnum;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface LernzielDAO extends JpaRepository<Lernziel, Long> {

    List<Lernziel> findByKind(KindEnum kind);

    List<Lernziel> findByParentId(Long parentId);

    Optional<Lernziel> findFirstByTitleIgnoreCase(String title);
}
