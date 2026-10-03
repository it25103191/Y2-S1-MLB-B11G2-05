package com.safari.tms.service;

import com.safari.tms.domain.Booking;
import com.safari.tms.domain.User;
import com.safari.tms.domain.enums.BookingStatus;
import com.safari.tms.domain.enums.ComplaintStatus;
import com.safari.tms.domain.enums.Role;
import com.safari.tms.dto.BookingDtos.BookingView;
import com.safari.tms.dto.RelationsDtos.*;
import com.safari.tms.exception.ApiException;
import com.safari.tms.repo.*;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.List;

/** Assembles the staff-facing "everything about this customer" screen. */
@Service
public class CustomerProfileService {

    private final UserRepository users;
    private final BookingRepository bookings;
    private final ComplaintRepository complaints;
    private final CommunicationLogRepository communications;
    private final NotificationLogRepository notifications;

    public CustomerProfileService(UserRepository users,
                                  BookingRepository bookings,
                                  ComplaintRepository complaints,
                                  CommunicationLogRepository communications,
                                  NotificationLogRepository notifications) {
        this.users = users;
        this.bookings = bookings;
        this.complaints = complaints;
        this.communications = communications;
        this.notifications = notifications;
    }

    @Transactional(readOnly = true)
    public List<CustomerSummaryView> listCustomers() {
        return users.findByRoleOrderByFullNameAsc(Role.CUSTOMER).stream()
                .map(this::summarise)
                .toList();
    }

    @Transactional(readOnly = true)
    public CustomerProfileView profile(Long customerId) {
        User customer = users.findById(customerId)
                .orElseThrow(() -> ApiException.notFound("Customer", customerId));
        if (customer.getRole() != Role.CUSTOMER) {
            throw ApiException.badRequest("That account is a staff member, not a customer.");
        }

        return new CustomerProfileView(
                summarise(customer),
                bookings.findForCustomer(customerId).stream().map(BookingView::of).toList(),
                complaints.findForCustomer(customerId).stream().map(ComplaintView::of).toList(),
                communications.findForCustomer(customerId).stream().map(CommunicationView::of).toList(),
                notifications.findForUser(customerId).stream().map(NotificationView::of).toList());
    }

    private CustomerSummaryView summarise(User customer) {
        List<Booking> theirBookings = bookings.findForCustomer(customer.getId());

        long active = theirBookings.stream().filter(b -> b.getStatus().isActive()).count();
        long cancelled = theirBookings.stream()
                .filter(b -> b.getStatus() == BookingStatus.CANCELLED).count();

        BigDecimal lifetime = theirBookings.stream()
                .filter(b -> b.getStatus() != BookingStatus.CANCELLED)
                .map(Booking::getAmountPaid)
                .reduce(BigDecimal.ZERO, BigDecimal::add);

        BigDecimal outstanding = theirBookings.stream()
                .filter(b -> b.getStatus() != BookingStatus.CANCELLED)
                .map(Booking::getBalanceDue)
                .filter(v -> v.compareTo(BigDecimal.ZERO) > 0)
                .reduce(BigDecimal.ZERO, BigDecimal::add);

        long openCases = complaints.findForCustomer(customer.getId()).stream()
                .filter(c -> c.getStatus() == ComplaintStatus.OPEN
                        || c.getStatus() == ComplaintStatus.IN_PROGRESS)
                .count();

        return new CustomerSummaryView(
                customer.getId(), customer.getFullName(), customer.getEmail(), customer.getPhone(),
                customer.getCreatedAt(), theirBookings.size(), active, cancelled,
                lifetime, outstanding, openCases);
    }
}
