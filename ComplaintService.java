package com.safari.tms.service;

import com.safari.tms.domain.Booking;
import com.safari.tms.domain.CommunicationLog;
import com.safari.tms.domain.Complaint;
import com.safari.tms.domain.User;
import com.safari.tms.domain.enums.*;
import com.safari.tms.dto.RelationsDtos.*;
import com.safari.tms.exception.ApiException;
import com.safari.tms.repo.*;
import com.safari.tms.service.message.CaseReferenceDecorator;
import com.safari.tms.service.message.CustomerMessage;
import com.safari.tms.service.message.GreetingDecorator;
import com.safari.tms.service.message.PlainMessage;
import com.safari.tms.service.message.SignatureDecorator;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.List;

/**
 * Customer cases: raise, update, escalate, notes and deletion.
 *
 * <p>Decorator pattern: every e-mail to the customer starts as a {@link PlainMessage} and is
 * wrapped in the decorators it needs (greeting, case reference, signature).
 */
@Service
public class ComplaintService {

    /** Who customer e-mails about cases are signed by. */
    private static final String RELATIONS_TEAM = "Customer Relations";

    private final ComplaintRepository complaints;
    private final CommunicationLogRepository communications;
    private final BookingRepository bookings;
    private final UserRepository users;
    private final ReferenceGenerator references;
    private final NotificationService notifications;
    private final ReplyTemplateService templates;

    public ComplaintService(ComplaintRepository complaints,
                            CommunicationLogRepository communications,
                            BookingRepository bookings,
                            UserRepository users,
                            ReferenceGenerator references,
                            NotificationService notifications,
                            ReplyTemplateService templates) {
        this.complaints = complaints;
        this.communications = communications;
        this.bookings = bookings;
        this.users = users;
        this.references = references;
        this.notifications = notifications;
        this.templates = templates;
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
        List<CommunicationLog> entries = communications.findForComplaint(complaintId);
        Long openingId = openingId(entries);
        return entries.stream().map(e -> CommunicationView.of(e, e.getId().equals(openingId))).toList();
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

        CustomerMessage ack = new SignatureDecorator(
                new GreetingDecorator(
                        new PlainMessage("We have your case " + saved.getReference(),
                                "Thanks for getting in touch. Your case " + saved.getReference()
                                        + " (\"" + saved.getSubject() + "\") is with our customer relations team."),
                        customer.getFullName()),
                RELATIONS_TEAM);
        send(saved, ack);

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
            CustomerMessage resolved = new SignatureDecorator(
                    new CaseReferenceDecorator(
                            new GreetingDecorator(
                                    new PlainMessage("Case " + saved.getReference() + " resolved",
                                            "Your case \"" + saved.getSubject() + "\" has been marked resolved. "
                                                    + (saved.getResolutionNotes() == null ? "" : saved.getResolutionNotes())),
                                    saved.getCustomer().getFullName()),
                            saved.getReference()),
                    RELATIONS_TEAM);
            send(saved, resolved);
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

        CustomerMessage escalated = new SignatureDecorator(
                new CaseReferenceDecorator(
                        new GreetingDecorator(
                                new PlainMessage("Case " + saved.getReference() + " escalated",
                                        "Your case has been escalated to our " + saved.getEscalatedToDepartment()
                                                + " team for a faster resolution. We will be in touch shortly."),
                                saved.getCustomer().getFullName()),
                        saved.getReference()),
                RELATIONS_TEAM);
        send(saved, escalated);

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

        if (request.templateId() != null && actor.getRole().isStaff()) {
            templates.recordUse(request.templateId());
        }

        c.setUpdatedAt(Instant.now());
        if (c.getStatus() == ComplaintStatus.OPEN && actor.getRole() != Role.CUSTOMER) {
            c.setStatus(ComplaintStatus.IN_PROGRESS);
        }
        complaints.save(c);

        if (request.notifyCustomer() && actor.getRole() != Role.CUSTOMER) {
            CustomerMessage reply = new CaseReferenceDecorator(
                    new PlainMessage(request.subject() == null ? "Update on case " + c.getReference() : request.subject(),
                            request.message().trim()),
                    c.getReference());
            send(c, reply);
        }

        return CommunicationView.of(entry);
    }

    /** E-mails a (decorated) message to the customer who raised the case. */
    private void send(Complaint c, CustomerMessage message) {
        notifications.email(c.getCustomer(), message.subject(), message.body(), "Complaint", c.getId());
    }

    /* ------------------------------------------------------------- Delete */

    /**
     * Customers may withdraw their own case while it is still open and before staff have replied.
     * Relations staff may delete any case (spam, duplicates). The case's timeline goes with it.
     */
    @Transactional
    public void delete(Long id, User caller) {
        Complaint c = requireVisible(id, caller);
        List<CommunicationLog> entries = communications.findByComplaintId(id);

        if (caller.getRole() == Role.CUSTOMER) {
            // A staff reply also moves the case to In progress, so check it first for the clearer message.
            boolean staffReplied = entries.stream()
                    .anyMatch(e -> e.getAuthor() != null && e.getAuthor().getRole().isStaff());
            if (staffReplied) {
                throw ApiException.badRequest("Our team has already replied to this case, so it can no longer"
                        + " be withdrawn. Add a reply instead.");
            }
            if (c.getStatus() != ComplaintStatus.OPEN) {
                throw ApiException.badRequest("You can only withdraw a case while it is still open. This one is "
                        + c.getStatus().name().toLowerCase().replace('_', ' ') + ".");
            }
        }

        communications.deleteAll(entries);
        complaints.delete(c);
    }

    /* ------------------------------------------------------ Note editing */

    @Transactional
    public CommunicationView updateNote(Long complaintId, Long noteId, NoteUpdateRequest request, User caller) {
        CommunicationLog note = requireEditableNote(complaintId, noteId, caller, "edited");

        note.setMessage(request.message().trim());
        if (request.subject() != null) {
            note.setSubject(request.subject().isBlank() ? null : request.subject().trim());
        }
        note.setEditedAt(Instant.now());
        CommunicationLog saved = communications.save(note);

        Complaint c = note.getComplaint();
        c.setUpdatedAt(Instant.now());
        complaints.save(c);

        return CommunicationView.of(saved, false);
    }

    @Transactional
    public void deleteNote(Long complaintId, Long noteId, User caller) {
        CommunicationLog note = requireEditableNote(complaintId, noteId, caller, "deleted");
        communications.delete(note);
    }

    /**
     * Only the author may change a note, only hand-written notes can change, and the opening
     * message stays put because it is the case itself.
     */
    private CommunicationLog requireEditableNote(Long complaintId, Long noteId, User caller, String verb) {
        requireVisible(complaintId, caller);
        List<CommunicationLog> entries = communications.findByComplaintId(complaintId);

        CommunicationLog note = entries.stream()
                .filter(e -> e.getId().equals(noteId))
                .findFirst()
                .orElseThrow(() -> ApiException.notFound("Note", noteId));

        if (note.getAuthor() == null || !note.getAuthor().getId().equals(caller.getId())) {
            throw ApiException.forbidden("You can only change notes you wrote yourself.");
        }
        if (!CommunicationView.isManual(note.getType())) {
            throw ApiException.badRequest("Status changes and escalations are part of the case's audit trail"
                    + " and can't be " + verb + ".");
        }
        if (note.getId().equals(openingId(entries))) {
            throw ApiException.badRequest("The opening message is the case itself and can't be " + verb
                    + ". Withdraw or close the case instead.");
        }
        return note;
    }

    /** The case's first entry by insertion order, which is always the customer's original message. */
    private Long openingId(List<CommunicationLog> entries) {
        return entries.stream().map(CommunicationLog::getId).min(Long::compare).orElse(null);
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
