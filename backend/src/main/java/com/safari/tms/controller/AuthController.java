package com.safari.tms.controller;

import com.safari.tms.dto.AuthDtos;
import com.safari.tms.dto.UserView;
import com.safari.tms.service.AuthService;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/auth")
public class AuthController {

    private final AuthService authService;

    public AuthController(AuthService authService) {
        this.authService = authService;
    }

    @PostMapping("/register")
    public ResponseEntity<AuthDtos.AuthResponse> register(@Valid @RequestBody AuthDtos.RegisterRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED).body(authService.register(request));
    }

    @PostMapping("/login")
    public AuthDtos.AuthResponse login(@Valid @RequestBody AuthDtos.LoginRequest request) {
        return authService.login(request);
    }

    /** Lets the SPA rehydrate the session from a stored token on page refresh. */
    @GetMapping("/me")
    public UserView me() {
        return UserView.of(authService.requireCurrentUser());
    }

    /** Saves the caller's own name, phone and currency (only the fields sent). */
    @PatchMapping("/me")
    public UserView updateMe(@Valid @RequestBody AuthDtos.ProfileUpdateRequest request) {
        return authService.updateProfile(request);
    }

    @PostMapping("/me/password")
    public ResponseEntity<Void> changePassword(@Valid @RequestBody AuthDtos.PasswordChangeRequest request) {
        authService.changePassword(request);
        return ResponseEntity.noContent().build();
    }

    /** A customer closes their own account (confirmed with their password). */
    @DeleteMapping("/me")
    public ResponseEntity<Void> closeAccount(@Valid @RequestBody AuthDtos.CloseAccountRequest request) {
        authService.closeAccount(request);
        return ResponseEntity.noContent().build();
    }
}
