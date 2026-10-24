package de.fisp.azubiprojekt.dao;

import de.fisp.azubiprojekt.entity.VersendeteEmail;
import de.fisp.azubiprojekt.util.EmailAnlassEnum;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface VersendeteEmailDAO extends JpaRepository<VersendeteEmail, Long> {

    boolean existsByPersonIdAndAnlassAndBezug(Long personId, EmailAnlassEnum anlass, String bezug);
}
