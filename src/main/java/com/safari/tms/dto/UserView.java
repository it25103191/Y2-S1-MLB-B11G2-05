package com.safari.tms.dto;

import com.safari.tms.domain.User;
import com.safari.tms.domain.enums.Role;

import java.time.Instant;

/** Safe projection of a user - never carries the password hash. */
public record UserView(
        Long id,
        String fullName,
        String email,
        String phone,
        Role role,
        String roleLabel,
        boolean staff,
        boolean active,
        Instant createdAt) {

    public static UserView of(User user) {
        return new UserView(
                user.getId(),
                user.getFullName(),
                user.getEmail(),
                user.getPhone(),
                user.getRole(),
                user.getRole().getLabel(),
                user.getRole().isStaff(),
                user.isActive(),
                user.getCreatedAt());
    }
}
