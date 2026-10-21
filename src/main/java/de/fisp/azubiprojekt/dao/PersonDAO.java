package de.fisp.azubiprojekt.dao;

import de.fisp.azubiprojekt.entity.Person;
import de.fisp.azubiprojekt.util.RoleEnum;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Collection;
import java.util.List;

@Repository
public interface PersonDAO extends JpaRepository<Person, Long> {

    List<Person> findByRole(RoleEnum role);

    List<Person> findByRoleIn(Collection<RoleEnum> roles);

    List<Person> findAllByOrderByLastNameAscFirstNameAsc();
}
