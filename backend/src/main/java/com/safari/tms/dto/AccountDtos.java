package com.safari.tms.dto;

import com.safari.tms.domain.User;
import com.safari.tms.domain.enums.Role;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

import java.time.Instant;

/** Staff account management, used by the Operations Manager. */
public final class AccountDtos {

    public record StaffCreateRequest(
            @NotBlank(message = "Full name is required")
            @Size(min = 2, max = 120, message = "Full name must be 2-120 characters")
            String fullName,

            @NotBlank(message = "Email is required")
            @Email(message = "Enter a valid email address")
            @Size(max = 180, message = "Email is too long")
            String email,

            @Pattern(regexp = "^$|^[0-9+()\\-\\s]{7,40}$", message = "Enter a valid phone number")
            String phone,

            @NotNull(message = "Choose a role")
            Role role,

            /** Given to the new staff member in person; they change it from My account. */
            @NotBlank(message = "Set a temporary password")
            @Size(min = 8, max = 72, message = "Temporary password must be at least 8 characters")
            String temporaryPassword) {
    }

    /** The email is the sign-in name, so it stays fixed once the account exists. */
    public record StaffUpdateRequest(
            @NotBlank(message = "Full name is required")
            @Size(min = 2, max = 120, message = "Full name must be 2-120 characters")
            String fullName,

            @Pattern(regexp = "^$|^[0-9+()\\-\\s]{7,40}$", message = "Enter a valid phone number")
            String phone,

            @NotNull(message = "Choose a role")
            Role role) {
    }

    public record StaffStatusRequest(
            @NotNull(message = "Say whether the account should be active")
            Boolean active) {
    }

    /**
     * One row on the Staff accounts page.
     *
     * @param openCases customer cases still assigned to this person (they block deactivation)
     * @param you       true for the manager's own row, which cannot be deactivated or removed
     */
    public record StaffAccountView(
            Long id,
            String fullName,
            String email,
            String phone,
            Role role,
            String roleLabel,
            boolean active,
            Instant createdAt,
            long openCases,
            boolean you) {

        public static StaffAccountView of(User user, long openCases, Long callerId) {
            return new StaffAccountView(user.getId(), user.getFullName(), user.getEmail(), user.getPhone(),
                    user.getRole(), user.getRole().getLabel(), user.isActive(), user.getCreatedAt(),
                    openCases, user.getId().equals(callerId));
        }
    }

    private AccountDtos() {
    }
}
