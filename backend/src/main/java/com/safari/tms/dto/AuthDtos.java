package com.safari.tms.dto;

import com.safari.tms.domain.enums.Currency;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

public final class AuthDtos {

    public record LoginRequest(
            @NotBlank(message = "Email is required")
            @Email(message = "Enter a valid email address")
            String email,

            @NotBlank(message = "Password is required")
            String password) {
    }

    public record RegisterRequest(
            @NotBlank(message = "Full name is required")
            @Size(min = 2, max = 120, message = "Full name must be 2-120 characters")
            String fullName,

            @NotBlank(message = "Email is required")
            @Email(message = "Enter a valid email address")
            @Size(max = 180, message = "Email is too long")
            String email,

            @NotBlank(message = "Password is required")
            @Size(min = 8, max = 72, message = "Password must be at least 8 characters")
            String password,

            @Pattern(regexp = "^$|^[0-9+()\\-\\s]{7,40}$", message = "Enter a valid phone number")
            String phone,

            /** LKR for Sri Lankan residents, USD for everyone else. Defaults to USD. */
            Currency preferredCurrency) {
    }

    /**
     * Changes to the caller's own profile. Fields left out (null) stay as they are; an empty phone
     * clears it. The email is the sign-in name, so it is not changed here.
     */
    public record ProfileUpdateRequest(
            @Size(min = 2, max = 120, message = "Full name must be 2-120 characters")
            String fullName,

            @Pattern(regexp = "^$|^[0-9+()\\-\\s]{7,40}$", message = "Enter a valid phone number")
            String phone,

            Currency preferredCurrency) {
    }

    public record PasswordChangeRequest(
            @NotBlank(message = "Enter your current password")
            String currentPassword,

            @NotBlank(message = "Choose a new password")
            @Size(min = 8, max = 72, message = "Password must be at least 8 characters")
            String newPassword) {
    }

    /** Closing an account is confirmed with the password, like any other sensitive change. */
    public record CloseAccountRequest(
            @NotBlank(message = "Enter your password to confirm")
            String password) {
    }

    public record AuthResponse(
            String token,
            String tokenType,
            long expiresInMinutes,
            UserView user) {

        public static AuthResponse of(String token, long minutes, UserView user) {
            return new AuthResponse(token, "Bearer", minutes, user);
        }
    }

    private AuthDtos() {
    }
}
