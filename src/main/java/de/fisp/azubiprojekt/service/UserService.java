package de.fisp.azubiprojekt.service;

import de.fisp.azubiprojekt.dao.UserDAO;
import de.fisp.azubiprojekt.entity.UserAuthentication;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Laedt Login-Konten fuer Spring Security. */
@Service
public class UserService implements UserDetailsService {

    private final UserDAO userDAO;

    public UserService(UserDAO userDAO) {
        this.userDAO = userDAO;
    }

    @Override
    @Transactional(readOnly = true)
    public UserAuthentication loadUserByUsername(String username) throws UsernameNotFoundException {
        return userDAO.findByUsernameIgnoreCase(username)
                .map(UserAuthentication::new)
                .orElseThrow(() -> new UsernameNotFoundException(username));
    }
}
