package com.example.demo.security;

import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * The only unauthenticated API surface.
 *
 * <p>Both endpoints answer a rejected credential with a 401 problem document
 * carrying {@code INVALID_CREDENTIALS}; the distinction between an unknown
 * user, a wrong password and an unusable refresh token is not exposed.
 */
@RestController
@RequestMapping("/api/v1/auth")
public class AuthController {

    private final AuthService authService;

    public AuthController(AuthService authService) {
        this.authService = authService;
    }

    @PostMapping("/login")
    public TokenResponse login(@RequestBody(required = false) LoginRequest request) {
        return authService.login(request);
    }

    @PostMapping("/refresh")
    public TokenResponse refresh(@RequestBody(required = false) RefreshRequest request) {
        return authService.refresh(request);
    }
}
