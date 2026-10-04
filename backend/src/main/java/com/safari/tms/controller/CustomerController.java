package com.safari.tms.controller;

import com.safari.tms.dto.RelationsDtos.CustomerProfileView;
import com.safari.tms.dto.RelationsDtos.CustomerSummaryView;
import com.safari.tms.dto.RelationsDtos.NotificationView;
import com.safari.tms.domain.User;
import com.safari.tms.dto.UserView;
import com.safari.tms.security.Roles;
import com.safari.tms.service.CustomerProfileService;
import com.safari.tms.service.NotificationService;
import com.safari.tms.repo.UserRepository;
import com.safari.tms.domain.enums.Role;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api")
@PreAuthorize(Roles.ANY_STAFF)
public class CustomerController {

    private final CustomerProfileService profiles;
    private final NotificationService notifications;
    private final UserRepository users;

    public CustomerController(CustomerProfileService profiles,
                              NotificationService notifications,
                              UserRepository users) {
        this.profiles = profiles;
        this.notifications = notifications;
        this.users = users;
    }

    @GetMapping("/customers")
    public List<CustomerSummaryView> customers() {
        return profiles.listCustomers();
    }

    @GetMapping("/customers/{id}")
    public CustomerProfileView profile(@PathVariable Long id) {
        return profiles.profile(id);
    }

    /** Staff directory, used to populate case-assignment pickers. Deactivated accounts are left out. */
    @GetMapping("/staff-users")
    public List<UserView> staffUsers() {
        return users.findByRoleInOrderByFullNameAsc(List.of(
                        Role.OPERATIONS_MANAGER, Role.CUSTOMER_RELATIONS_OFFICER,
                        Role.SAFARI_VEHICLE_COORDINATOR, Role.FINANCE_RESERVATIONS_EXECUTIVE,
                        Role.FINANCE_ACCOUNTS_OFFICER))
                .stream().filter(User::isActive).map(UserView::of).toList();
    }

    @GetMapping("/notifications")
    public List<NotificationView> notificationLog() {
        return notifications.findAll().stream().map(NotificationView::of).toList();
    }
}
