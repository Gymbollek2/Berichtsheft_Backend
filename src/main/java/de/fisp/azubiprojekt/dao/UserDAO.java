package de.fisp.azubiprojekt.dao;

import de.fisp.azubiprojekt.entity.Person;
import de.fisp.azubiprojekt.entity.Users;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface UserDAO extends JpaRepository<Users, Long> {

    Optional<Users> findByUsernameIgnoreCase(String username);

    Optional<Users> findByPerson(Person person);

    boolean existsByUsernameIgnoreCase(String username);
}
