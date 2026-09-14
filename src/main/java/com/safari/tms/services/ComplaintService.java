package com.safari.tms.service;

import com.safari.tms.domain.Booking;
import com.safari.tms.domain.CommunicationLog;
import com.safari.tms.domain.Complaint;
import com.safari.tms.domain.User;
import com.safari.tms.domain.enums.*;
import com.safari.tms.dto.RelationsDtos.*;
import com.safari.tms.exception.ApiException;
import com.safari.tms.repo.*;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.List;

@Service
public class ComplaintService {

    private final ComplaintRepository complaints;
    private final CommunicationLogRepository communications;
    private final BookingRepository bookings;
    private final UserRepository users;
    private final ReferenceGenerator references;
    private final NotificationService notifications;

    public ComplaintService(ComplaintRepository complaints,
                            CommunicationLogRepository communications,
                            BookingRepository bookings,
                            UserRepository users,
                            ReferenceGenerator references,
                            NotificationService notifications) {
        this.complaints = complaints;
        this.communications = communications;
        this.bookings = bookings;
        this.users = users;
        this.references = references;
        this.notifications = notifications;
    }

    /* ------------------------------------------------------------ Queries */

    @Transactional(readOnly = true)
    public List<ComplaintView> findAll() {
        return complaints.findAllDetailed().stream().map(ComplaintView::of).toList();
    }

    @Transactional(readOnly = true)
    public List<ComplaintView> findForCustomer(Long customerId) {
        return complaints.findForCustomer(customerId).stream().map(ComplaintView::of).toList();
    }

    @Transactional(readOnly = true)
    public ComplaintView findOne(Long id, User caller) {
        return ComplaintView.of(requireVisible(id, caller));
    }

    @Transactional(readOnly = true)
    public List<CommunicationView> timeline(Long complaintId, User caller) {
        requireVisible(complaintId, caller);
        return communications.findForComplaint(complaintId).stream().map(CommunicationView::of).toList();
    }

    /* ------------------------------------------------------------- Create */

    /** Raised by a customer from the support page. */
    @Transactional
    public ComplaintView create(User customer, ComplaintRequest request) {
        Complaint c = new Complaint();
        c.setReference(references.complaint());
        c.setCustomer(customer);
        c.setSubject(request.subject().trim());
        c.setDescription(request.description().trim());
        c.setCategory(request.category() == null ? ComplaintCategory.OTHER : request.category());
        c.setPriority(derivePriority(request.category()));
        c.setStatus(ComplaintStatus.OPEN);

        if (request.bookingId() != null) {
            Booking booking = bookings.findDetailById(request.bookingId())
                    .orElseThrow(() -> ApiException.notFound("Booking", request.bookingId()));
            if (!booking.getCustomer().getId().equals(customer.getId())) {
                throw ApiException.forbidden("That booking belongs to another customer.");
            }
            c.setBooking(booking);
        }

        Complaint saved = complaints.save(c);

        log(saved, customer, CommunicationType.IN_APP_NOTE, CommunicationDirection.INBOUND,
                saved.getSubject(), saved.getDescription(), customer);

        notifications.email(customer, "We have your case " + saved.getReference(),
                "Thanks for getting in touch. Your case " + saved.getReference()
                        + " (\"" + saved.getSubject() + "\") is with our customer relations team.",
                "Complaint", saved.getId());

        return ComplaintView.of(saved);
    }

    /* ------------------------------------------------------------- Update */

    @Transactional
    public ComplaintView update(Long id, ComplaintUpdateRequest request, User actor) {
        Complaint c = complaints.findDetailById(id)
                .orElseThrow(() -> ApiException.notFound("Complaint", id));

        StringBuilder changes = new StringBuilder();

        if (request.status() != null && request.status() != c.getStatus()) {
            changes.append("Status ").append(c.getStatus()).append(" → ").append(request.status()).append(". ");
            c.setStatus(request.status());
            if (request.status() == ComplaintStatus.RESOLVED || request.status() == ComplaintStatus.UNRESOLVED) {
                c.setResolvedAt(Instant.now());
            } else {
                c.setResolvedAt(null);
            }
        }

        if (request.priority() != null && request.priority() != c.getPriority()) {
            changes.append("Priority ").append(c.getPriority()).append(" → ").append(request.priority()).append(". ");
            c.setPriority(request.priority());
        }

        if (request.assignedToId() != null) {
            User assignee = users.findById(request.assignedToId())
                    .orElseThrow(() -> ApiException.notFound("User", request.assignedToId()));
            if (assignee.getRole() == Role.CUSTOMER) {
                throw ApiException.badRequest("Cases can only be assigned to staff.");
            }
            if (c.getAssignedTo() == null || !c.getAssignedTo().getId().equals(assignee.getId())) {
                changes.append("Assigned to ").append(assignee.getFullName()).append(". ");
                c.setAssignedTo(assignee);
            }
        }

        if (request.resolutionNotes() != null && !request.resolutionNotes().isBlank()) {
            c.setResolutionNotes(request.resolutionNotes().trim());
        }

        c.setUpdatedAt(Instant.now());
        Complaint saved = complaints.save(c);

        String message = (request.note() == null || request.note().isBlank())
                ? changes.toString().trim()
                : request.note().trim() + (changes.length() > 0 ? "\n\n(" + changes.toString().trim() + ")" : "");

        if (!message.isBlank()) {
            log(saved, saved.getCustomer(), CommunicationType.STATUS_CHANGE,
                    CommunicationDirection.INTERNAL, "Case update", message, actor);
        }

        if (saved.getStatus() == ComplaintStatus.RESOLVED) {
            notifications.email(saved.getCustomer(), "Case " + saved.getReference() + " resolved",
                    "Your case \"" + saved.getSubject() + "\" has been marked resolved. "
                            + (saved.getResolutionNotes() == null ? "" : saved.getResolutionNotes()),
                    "Complaint", saved.getId());
        }

        return ComplaintView.of(saved);
    }

    /* ---------------------------------------------------------- Escalate */

    @Transactional
    public ComplaintView escalate(Long id, EscalateRequest request, User actor) {
        Complaint c = complaints.findDetailById(id)
                .orElseThrow(() -> ApiException.notFound("Complaint", id));

        if (c.getStatus() == ComplaintStatus.RESOLVED) {
            throw ApiException.badRequest("Resolved cases cannot be escalated. Reopen it first.");
        }

        c.setEscalatedToDepartment(request.department().trim());
        c.setEscalatedAt(Instant.now());
        c.setStatus(ComplaintStatus.IN_PROGRESS);
        c.setPriority(request.priority() == null
                ? bump(c.getPriority())
                : request.priority());
        c.setUpdatedAt(Instant.now());

        Complaint saved = complaints.save(c);

        log(saved, saved.getCustomer(), CommunicationType.ESCALATION, CommunicationDirection.INTERNAL,
                "Escalated to " + saved.getEscalatedToDepartment(), request.reason().trim(), actor);

        notifications.email(saved.getCustomer(), "Case " + saved.getReference() + " escalated",
                "Your case has been escalated to our " + saved.getEscalatedToDepartment()
                        + " team for a faster resolution. We will be in touch shortly.",
                "Complaint", saved.getId());

        return ComplaintView.of(saved);
    }

    /* -------------------------------------------------------- Timeline add */

    @Transactional
    public CommunicationView addNote(Long complaintId, NoteRequest request, User actor) {
        Complaint c = requireVisible(complaintId, actor);

        CommunicationDirection direction = request.direction() != null
                ? request.direction()
                : (actor.getRole() == Role.CUSTOMER ? CommunicationDirection.INBOUND : CommunicationDirection.OUTBOUND);

        CommunicationType type = request.type() != null ? request.type() : CommunicationType.IN_APP_NOTE;

        CommunicationLog entry = log(c, c.getCustomer(), type, direction,
                request.subject(), request.message().trim(), actor);

        c.setUpdatedAt(Instant.now());
        if (c.getStatus() == ComplaintStatus.OPEN && actor.getRole() != Role.CUSTOMER) {
            c.setStatus(ComplaintStatus.IN_PROGRESS);
        }
        complaints.save(c);

        if (request.notifyCustomer() && actor.getRole() != Role.CUSTOMER) {
            notifications.email(c.getCustomer(),
                    request.subject() == null ? "Update on case " + c.getReference() : request.subject(),
                    request.message().trim(), "Complaint", c.getId());
        }

        return CommunicationView.of(entry);
    }

    /* ------------------------------------------------------------ Helpers */

    private CommunicationLog log(Complaint complaint, User customer, CommunicationType type,
                                 CommunicationDirection direction, String subject, String message, User author) {
        CommunicationLog entry = new CommunicationLog();
        entry.setCustomer(customer);
        entry.setComplaint(complaint);
        entry.setBooking(complaint == null ? null : complaint.getBooking());
        entry.setType(type);
        entry.setDirection(direction);
        entry.setSubject(subject);
        entry.setMessage(message);
        entry.setAuthor(author);
        return communications.save(entry);
    }

    private ComplaintPriority derivePriority(ComplaintCategory category) {
        if (category == null) return ComplaintPriority.MEDIUM;
        return switch (category) {
            case PAYMENT, BOOKING -> ComplaintPriority.HIGH;
            case GENERAL_INQUIRY -> ComplaintPriority.LOW;
            default -> ComplaintPriority.MEDIUM;
        };
    }

    private ComplaintPriority bump(ComplaintPriority current) {
        return switch (current) {
            case LOW -> ComplaintPriority.MEDIUM;
            case MEDIUM -> ComplaintPriority.HIGH;
            default -> ComplaintPriority.CRITICAL;
        };
    }

    private Complaint requireVisible(Long id, User caller) {
        Complaint c = complaints.findDetailById(id)
                .orElseThrow(() -> ApiException.notFound("Complaint", id));
        if (caller.getRole() == Role.CUSTOMER && !c.getCustomer().getId().equals(caller.getId())) {
            throw ApiException.forbidden("That case belongs to another customer.");
        }
        return c;
    }
}
