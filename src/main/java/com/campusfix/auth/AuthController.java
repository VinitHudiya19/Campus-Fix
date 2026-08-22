package com.campusfix.auth;

import com.campusfix.auth.dto.ChangeMyPasswordRequest;
import com.campusfix.auth.dto.CurrentUserResponse;
import com.campusfix.auth.dto.LoginRequest;
import com.campusfix.auth.dto.LoginResponse;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/auth")
public class AuthController {

    private final AuthService authService;

    public AuthController(AuthService authService) {
        this.authService = authService;
    }

    @PostMapping("/login")
    public LoginResponse login(@Valid @RequestBody LoginRequest request) {
        return authService.login(request);
    }

    /** Lets the browser find out who it is signed in as after a page refresh. */
    @GetMapping("/me")
    public CurrentUserResponse me() {
        return authService.currentUser();
    }

    @PutMapping("/password")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void changeMyPassword(@Valid @RequestBody ChangeMyPasswordRequest request) {
        authService.changeMyPassword(request);
    }
}
