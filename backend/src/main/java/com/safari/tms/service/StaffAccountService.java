package com.safari.tms.service;

import com.safari.tms.domain.User;
import com.safari.tms.domain.enums.ComplaintStatus;
import com.safari.tms.domain.enums.Role;
import com.safari.tms.dto.AccountDtos.StaffAccountView;
import com.safari.tms.dto.AccountDtos.StaffCreateRequest;
import com.safari.tms.dto.AccountDtos.StaffUpdateRequest;
import com.safari.tms.exception.ApiException;
import com.safari.tms.repo.ComplaintRepository;
import com.safari.tms.repo.NotificationLogRepository;
import com.safari.tms.repo.UserRepository;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Arrays;
import java.util.List;

/**
 * Staff accounts: the Operations Manager adds employees, changes their details and role, and
 * deactivates or removes accounts.
 *
 * <p>Deactivating is the normal way to stop someone signing in, because their name stays on the
 * payments, permits and case notes they handled. Removing is only possible for an account that
 * has never been used for any of that.
 *
 * <p>A manager can't change their own role, deactivate or remove themselves, and only an active
 * manager can reach these actions, so the team always keeps at least one Operations Manager.
 */
@Service
public class StaffAccountService {

    private static final List<Role> STAFF_ROLES = Arrays.stream(Role.values()).filter(Role::isStaff).toList();
    private static final List<ComplaintStatus> OPEN_CASES = List.of(ComplaintStatus.OPEN, ComplaintStatus.IN_PROGRESS);

    private final UserRepository users;
    private final ComplaintRepository complaints;
    private final NotificationLogRepository notificationLogs;
    private final PasswordEncoder passwordEncoder;
    private final NotificationService notifications;

    public StaffAccountService(UserRepository users,
                               ComplaintRepository complaints,
                               NotificationLogRepository notificationLogs,
                               PasswordEncoder passwordEncoder,
                               NotificationService notifications) {
        this.users = users;
        this.complaints = complaints;
        this.notificationLogs = notificationLogs;
        this.passwordEncoder = passwordEncoder;
        this.notifications = notifications;
    }

    @Transactional(readOnly = true)
    public List<StaffAccountView> list(User manager) {
        return users.findByRoleInOrderByFullNameAsc(STAFF_ROLES).stream()
                .map(u -> view(u, manager))
                .toList();
    }

    @Transactional
    public StaffAccountView create(StaffCreateRequest request, User manager) {
        requireStaffRole(request.role());
        String email = request.email().trim().toLowerCase();
        if (users.existsByEmailIgnoreCase(email)) {
            throw ApiException.conflict("An account already exists for " + email);
        }

        User user = new User(request.fullName().trim(), email, passwordEncoder.encode(request.temporaryPassword()),
                blankToNull(request.phone()), request.role());
        User saved = users.save(user);

        // The password is handed over in person, never e-mailed.
        notifications.email(saved, "Welcome to the Ceylon Trails team",
                "Hi " + saved.getFullName() + ", " + manager.getFullName() + " has set up your staff account as "
                        + saved.getRole().getLabel() + ". Sign in with this e-mail address and the temporary "
                        + "password you were given, then choose your own password under My account.",
                "User", saved.getId());

        return view(saved, manager);
    }

    @Transactional
    public StaffAccountView update(Long id, StaffUpdateRequest request, User manager) {
        User user = requireStaff(id);
        requireStaffRole(request.role());

        if (request.role() != user.getRole()) {
            if (user.getId().equals(manager.getId())) {
                throw ApiException.badRequest("You can't change your own role. Ask another Operations Manager.");
            }
        }

        user.setFullName(request.fullName().trim());
        user.setPhone(blankToNull(request.phone()));
        user.setRole(request.role());
        return view(users.save(user), manager);
    }

    /** Switches an account off (they can no longer sign in) or back on. */
    @Transactional
    public StaffAccountView setActive(Long id, boolean active, User manager) {
        User user = requireStaff(id);
        if (user.isActive() == active) {
            return view(user, manager);
        }

        if (!active) {
            if (user.getId().equals(manager.getId())) {
                throw ApiException.badRequest("You can't deactivate your own account.");
            }
            long openCases = complaints.countByAssignedToIdAndStatusIn(user.getId(), OPEN_CASES);
            if (openCases > 0) {
                throw ApiException.conflict(user.getFullName() + " still owns " + openCases + " open customer case(s). "
                        + "Reassign them on the Complaints page first.");
            }
        }

        user.setActive(active);
        User saved = users.save(user);
        if (active) {
            notifications.email(saved, "Your Ceylon Trails account is active again",
                    "Hi " + saved.getFullName() + ", " + manager.getFullName()
                            + " has reactivated your staff account. You can sign in again as before.",
                    "User", saved.getId());
        }
        return view(saved, manager);
    }

    /**
     * Permanently removes an account that was never used. The database's foreign keys know every
     * table that refers to a user, so instead of checking each one here we try the delete and turn
     * a refusal into a clear message.
     */
    @Transactional
    public void remove(Long id, User manager) {
        User user = requireStaff(id);
        if (user.getId().equals(manager.getId())) {
            throw ApiException.badRequest("You can't remove your own account.");
        }

        try {
            // E-mails sent to them are only delivery records, so they go with the account.
            notificationLogs.deleteAll(notificationLogs.findForUser(user.getId()));
            users.delete(user);
            users.flush();
        } catch (DataIntegrityViolationException inUse) {
            throw ApiException.conflict(user.getFullName() + " has worked on bookings, payments, permits or cases, "
                    + "so removing the account would lose that history. Deactivate it instead.");
        }
    }

    private StaffAccountView view(User user, User manager) {
        long openCases = complaints.countByAssignedToIdAndStatusIn(user.getId(), OPEN_CASES);
        return StaffAccountView.of(user, openCases, manager.getId());
    }

    private User requireStaff(Long id) {
        User user = users.findById(id).orElseThrow(() -> ApiException.notFound("Staff account", id));
        if (!user.getRole().isStaff()) {
            throw ApiException.notFound("Staff account", id);
        }
        return user;
    }

    private void requireStaffRole(Role role) {
        if (!role.isStaff()) {
            throw ApiException.badRequest("Choose a staff role. Customers create their own accounts by signing up.");
        }
    }

    private static String blankToNull(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }
}
