package de.fisp.azubiprojekt.controller;

import de.fisp.azubiprojekt.dto.request.LoginRequestDTO;
import de.fisp.azubiprojekt.dto.request.PasswordChangeRequestDTO;
import de.fisp.azubiprojekt.dto.response.LoginResponseDTO;
import de.fisp.azubiprojekt.dto.response.PersonResponseDTO;
import de.fisp.azubiprojekt.service.AuthService;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.*;

/** Anmeldung und eigenes Konto. */
@RestController
@RequestMapping("/v1/passport")
public class AuthController {

    private final AuthService authService;

    public AuthController(AuthService authService) {
        this.authService = authService;
    }

    @PostMapping("/login")
    public LoginResponseDTO login(@Valid @RequestBody LoginRequestDTO request) {
        return authService.login(request);
    }

    @GetMapping("/me")
    public PersonResponseDTO me() {
        return authService.me();
    }

    @PostMapping("/change-password")
    public LoginResponseDTO changePassword(@Valid @RequestBody PasswordChangeRequestDTO request) {
        return authService.changePassword(request);
    }
}
