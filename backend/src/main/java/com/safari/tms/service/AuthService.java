package com.safari.tms.service;

import com.safari.tms.domain.Booking;
import com.safari.tms.domain.User;
import com.safari.tms.domain.enums.Currency;
import com.safari.tms.domain.enums.RefundStatus;
import com.safari.tms.domain.enums.Role;
import com.safari.tms.dto.AuthDtos;
import com.safari.tms.dto.UserView;
import com.safari.tms.exception.ApiException;
import com.safari.tms.repo.BookingRepository;
import com.safari.tms.repo.NotificationLogRepository;
import com.safari.tms.repo.RefundRepository;
import com.safari.tms.repo.UserRepository;
import com.safari.tms.security.AppUserDetails;
import com.safari.tms.security.JwtService;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

@Service
public class AuthService {

    private final UserRepository users;
    private final PasswordEncoder passwordEncoder;
    private final AuthenticationManager authenticationManager;
    private final JwtService jwtService;
    private final NotificationService notifications;
    private final BookingRepository bookings;
    private final RefundRepository refunds;
    private final NotificationLogRepository notificationLogs;

    public AuthService(UserRepository users,
                       PasswordEncoder passwordEncoder,
                       AuthenticationManager authenticationManager,
                       JwtService jwtService,
                       NotificationService notifications,
                       BookingRepository bookings,
                       RefundRepository refunds,
                       NotificationLogRepository notificationLogs) {
        this.users = users;
        this.passwordEncoder = passwordEncoder;
        this.authenticationManager = authenticationManager;
        this.jwtService = jwtService;
        this.notifications = notifications;
        this.bookings = bookings;
        this.refunds = refunds;
        this.notificationLogs = notificationLogs;
    }

    /** Public sign-up. Self-service registration always produces a CUSTOMER account. */
    @Transactional
    public AuthDtos.AuthResponse register(AuthDtos.RegisterRequest request) {
        String email = request.email().trim().toLowerCase();
        if (users.existsByEmailIgnoreCase(email)) {
            throw ApiException.conflict("An account already exists for " + email);
        }

        User user = new User(
                request.fullName().trim(),
                email,
                passwordEncoder.encode(request.password()),
                request.phone() == null || request.phone().isBlank() ? null : request.phone().trim(),
                Role.CUSTOMER);
        user.setPreferredCurrency(request.preferredCurrency() == null ? Currency.USD : request.preferredCurrency());
        User saved = users.save(user);

        notifications.email(saved, "Welcome to Ceylon Trails",
                "Hi " + saved.getFullName() + ", your account is ready. "
                        + "Browse our Sri Lankan safaris and book your first trip.",
                "User", saved.getId());

        return AuthDtos.AuthResponse.of(
                jwtService.generateToken(saved), jwtService.getExpirationMinutes(), UserView.of(saved));
    }

    @Transactional(readOnly = true)
    public AuthDtos.AuthResponse login(AuthDtos.LoginRequest request) {
        Authentication authentication = authenticationManager.authenticate(
                new UsernamePasswordAuthenticationToken(request.email().trim(), request.password()));

        AppUserDetails principal = (AppUserDetails) authentication.getPrincipal();
        User user = principal.getUser();

        return AuthDtos.AuthResponse.of(
                jwtService.generateToken(user), jwtService.getExpirationMinutes(), UserView.of(user));
    }

    /** Saves the fields the caller sent; anything left out stays as it was. */
    @Transactional
    public UserView updateProfile(AuthDtos.ProfileUpdateRequest request) {
        User user = requireCurrentUser();
        if (request.fullName() != null) {
            String name = request.fullName().trim();
            if (name.length() < 2) {
                throw ApiException.invalidField("fullName", "Full name must be 2-120 characters");
            }
            user.setFullName(name);
        }
        if (request.phone() != null) {
            user.setPhone(request.phone().isBlank() ? null : request.phone().trim());
        }
        if (request.preferredCurrency() != null) {
            user.setPreferredCurrency(request.preferredCurrency());
        }
        return UserView.of(users.save(user));
    }

    @Transactional
    public void changePassword(AuthDtos.PasswordChangeRequest request) {
        User user = requireCurrentUser();
        requirePassword(user, request.currentPassword(), "currentPassword");
        if (passwordEncoder.matches(request.newPassword(), user.getPasswordHash())) {
            throw ApiException.invalidField("newPassword", "Choose a password different from your current one");
        }

        user.setPasswordHash(passwordEncoder.encode(request.newPassword()));
        users.save(user);

        notifications.email(user, "Your password was changed",
                "Hi " + user.getFullName() + ", the password for your Ceylon Trails account was just changed. "
                        + "If this wasn't you, contact us straight away.",
                "User", user.getId());
    }

    /**
     * Closes a customer's own account. Their bookings, payments and support cases are kept for our
     * accounts, but the account itself no longer says who they were: the name, email and phone are
     * replaced, the password stops working and the e-mails we sent them are deleted.
     */
    @Transactional
    public void closeAccount(AuthDtos.CloseAccountRequest request) {
        User user = requireCurrentUser();
        if (user.getRole().isStaff()) {
            throw ApiException.forbidden("Staff accounts are closed by the Operations Manager.");
        }
        requirePassword(user, request.password(), "password");

        List<String> upcoming = bookings.findForCustomer(user.getId()).stream()
                .filter(b -> b.getStatus().isActive() && !b.getTripDate().isBefore(LocalDate.now()))
                .map(Booking::getBookingReference)
                .toList();
        if (!upcoming.isEmpty()) {
            throw ApiException.conflict("You still have " + upcoming.size() + " upcoming booking(s) ("
                    + String.join(", ", upcoming) + "). Cancel them before closing your account.");
        }
        if (refunds.existsByBookingCustomerIdAndStatusIn(user.getId(),
                List.of(RefundStatus.REQUESTED, RefundStatus.APPROVED))) {
            throw ApiException.conflict("A refund is still being processed for you. "
                    + "You can close your account once it has been paid.");
        }

        notificationLogs.deleteAll(notificationLogs.findForUser(user.getId()));
        user.setFullName("Closed account");
        user.setEmail("closed-" + user.getId() + "@closed.ceylontrails.lk");
        user.setPhone(null);
        user.setPasswordHash(passwordEncoder.encode(UUID.randomUUID().toString()));
        user.setActive(false);
        users.save(user);
    }

    private void requirePassword(User user, String password, String field) {
        if (!passwordEncoder.matches(password, user.getPasswordHash())) {
            throw ApiException.invalidField(field, "That password is incorrect");
        }
    }

    /** Resolves the caller from the security context - never from a client-supplied id. */
    @Transactional(readOnly = true)
    public User requireCurrentUser() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth == null || !(auth.getPrincipal() instanceof AppUserDetails details)) {
            throw new ApiException(org.springframework.http.HttpStatus.UNAUTHORIZED,
                    "You need to sign in to continue.");
        }
        return users.findById(details.getId())
                .orElseThrow(() -> new ApiException(org.springframework.http.HttpStatus.UNAUTHORIZED,
                        "Your account is no longer available."));
    }
}
