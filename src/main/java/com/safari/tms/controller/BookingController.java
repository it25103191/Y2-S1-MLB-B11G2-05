package com.safari.tms.web;

import com.safari.tms.domain.User;
import com.safari.tms.domain.enums.Role;
import com.safari.tms.dto.BookingDtos.BookingRequest;
import com.safari.tms.dto.BookingDtos.BookingUpdateRequest;
import com.safari.tms.dto.BookingDtos.BookingView;
import com.safari.tms.dto.BookingDtos.CancelRequest;
import com.safari.tms.dto.BookingDtos.StatusChangeRequest;
import com.safari.tms.dto.PermitDtos;
import com.safari.tms.security.Roles;
import com.safari.tms.service.AuthService;
import com.safari.tms.service.BookingService;
import com.safari.tms.service.PermitService;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/bookings")
public class BookingController {



    public BookingController(BookingService bookingService, AuthService authService,
                             PermitService permitService) {
        this.bookingService = bookingService;
        this.authService = authService;
        this.permitService = permitService;
    }

    /** Staff see the whole book; customers see only their own trips. */
    @GetMapping
    public List<BookingView> list() {
        User caller = authService.requireCurrentUser();
        return caller.getRole() == Role.CUSTOMER
                ? bookingService.findForCustomer(caller.getId())
                : bookingService.findAll();
    }

    @GetMapping("/mine")
    public List<BookingView> mine() {
        return bookingService.findForCustomer(authService.requireCurrentUser().getId());
    }

    @GetMapping("/awaiting-assignment")
    @PreAuthorize(Roles.OPERATIONS)
    public List<BookingView> awaitingAssignment() {
        return bookingService.findAwaitingAssignment();
    }

    @GetMapping("/{id}")
    public BookingView detail(@PathVariable Long id) {
        return bookingService.findOne(id, authService.requireCurrentUser());
    }

    /**
     * Permits covering this booking. Ownership is checked first, so a customer can see the permit
     * status for their own trip without being granted access to the whole permits module.
     */
    @GetMapping("/{id}/permits")
    public List<PermitDtos.PermitView> permits(@PathVariable Long id) {
        bookingService.findOne(id, authService.requireCurrentUser());
        return permitService.findForBooking(id);
    }

    @PostMapping
    @PreAuthorize("hasRole('" + Roles.CUSTOMER + "')")
    public ResponseEntity<BookingView> create(@Valid @RequestBody BookingRequest request) {
        User customer = authService.requireCurrentUser();
        return ResponseEntity.status(HttpStatus.CREATED).body(bookingService.create(customer, request));
    }

    /** Owner or staff may change a pending booking. */
    @PutMapping("/{id}")
    public BookingView update(@PathVariable Long id, @Valid @RequestBody BookingUpdateRequest request) {
        return bookingService.update(id, authService.requireCurrentUser(), request);
    }

    /** Owner or staff may delete a pending booking that has nothing attached to it. */
    @DeleteMapping("/{id}")
    public ResponseEntity<Void> delete(@PathVariable Long id) {
        bookingService.delete(id, authService.requireCurrentUser());
        return ResponseEntity.noContent().build();
    }

    @PostMapping("/{id}/cancel")
    public BookingView cancel(@PathVariable Long id, @RequestBody(required = false) CancelRequest request) {
        return bookingService.cancel(id, authService.requireCurrentUser(),
                request == null ? null : request.reason());
    }

    @PatchMapping("/{id}/status")
    @PreAuthorize(Roles.ANY_STAFF)
    public BookingView changeStatus(@PathVariable Long id, @Valid @RequestBody StatusChangeRequest request) {
        return bookingService.changeStatus(id, request.status());
    }
}
