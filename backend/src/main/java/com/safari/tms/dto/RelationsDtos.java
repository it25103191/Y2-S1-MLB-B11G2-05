package com.safari.tms.dto;

import com.safari.tms.domain.CommunicationLog;
import com.safari.tms.domain.Complaint;
import com.safari.tms.domain.NotificationLog;
import com.safari.tms.domain.enums.*;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.time.Instant;
import java.util.List;

public final class RelationsDtos {

    /* --------------------------------------------------------- Complaints */

    public record ComplaintRequest(
            @NotBlank(message = "A short subject is required")
            @Size(max = 200) String subject,

            @NotBlank(message = "Please describe what happened")
            @Size(max = 4000) String description,

            ComplaintCategory category,

            /** Optional - links the case to a specific trip. */
            Long bookingId) {
    }

    public record ComplaintUpdateRequest(
            ComplaintStatus status,
            ComplaintPriority priority,
            Long assignedToId,
            @Size(max = 2000) String resolutionNotes,
            /** Optional note appended to the case timeline alongside the change. */
            @Size(max = 4000) String note) {
    }

    public record NoteRequest(
            @NotBlank(message = "Message cannot be empty")
            @Size(max = 4000) String message,

            CommunicationType type,
            CommunicationDirection direction,
            @Size(max = 200) String subject,

            /** When true a notification record is written for the customer as well. */
            boolean notifyCustomer,

            /** Set when the reply was started from a template, so its usage can be counted. */
            Long templateId) {
    }

    public record NoteUpdateRequest(
            @NotBlank(message = "Message cannot be empty")
            @Size(max = 4000) String message,

            @Size(max = 200) String subject) {
    }

    public record EscalateRequest(
            @NotBlank(message = "Choose a department to escalate to")
            @Size(max = 80) String department,

            @NotBlank(message = "Explain why this is being escalated")
            @Size(max = 2000) String reason,

            ComplaintPriority priority) {
    }

    public record ComplaintView(
            Long id,
            String reference,
            Long customerId,
            String customerName,
            String customerEmail,
            Long bookingId,
            String bookingReference,
            String packageName,
            String subject,
            String description,
            ComplaintCategory category,
            ComplaintPriority priority,
            ComplaintStatus status,
            Long assignedToId,
            String assignedToName,
            String escalatedToDepartment,
            Instant escalatedAt,
            String resolutionNotes,
            Instant resolvedAt,
            Instant createdAt,
            Instant updatedAt,
            long ageDays) {

        public static ComplaintView of(Complaint c) {
            long age = java.time.Duration.between(c.getCreatedAt(), Instant.now()).toDays();
            return new ComplaintView(
                    c.getId(), c.getReference(),
                    c.getCustomer().getId(), c.getCustomer().getFullName(), c.getCustomer().getEmail(),
                    c.getBooking() == null ? null : c.getBooking().getId(),
                    c.getBooking() == null ? null : c.getBooking().getBookingReference(),
                    c.getBooking() == null ? null : c.getBooking().getSafariPackage().getName(),
                    c.getSubject(), c.getDescription(), c.getCategory(), c.getPriority(), c.getStatus(),
                    c.getAssignedTo() == null ? null : c.getAssignedTo().getId(),
                    c.getAssignedTo() == null ? null : c.getAssignedTo().getFullName(),
                    c.getEscalatedToDepartment(), c.getEscalatedAt(),
                    c.getResolutionNotes(), c.getResolvedAt(),
                    c.getCreatedAt(), c.getUpdatedAt(), age);
        }
    }

    /* ------------------------------------------------------ Communication */

    public record CommunicationView(
            Long id,
            Long customerId,
            String customerName,
            Long complaintId,
            Long bookingId,
            CommunicationType type,
            CommunicationDirection direction,
            String subject,
            String message,
            Long authorId,
            String authorName,
            boolean authorIsStaff,
            Instant createdAt,
            Instant editedAt,

            /**
             * True when nobody may edit or delete the entry: status changes, escalations and
             * system entries are the audit trail, and the opening message is the case itself.
             */
            boolean locked) {

        /** Types a person typed by hand, as opposed to entries the system records. */
        public static boolean isManual(CommunicationType type) {
            return type == CommunicationType.IN_APP_NOTE || type == CommunicationType.EMAIL
                    || type == CommunicationType.PHONE_CALL || type == CommunicationType.SMS;
        }

        public static CommunicationView of(CommunicationLog c) {
            return of(c, false);
        }

        public static CommunicationView of(CommunicationLog c, boolean openingMessage) {
            return new CommunicationView(
                    c.getId(),
                    c.getCustomer().getId(),
                    c.getCustomer().getFullName(),
                    c.getComplaint() == null ? null : c.getComplaint().getId(),
                    c.getBooking() == null ? null : c.getBooking().getId(),
                    c.getType(), c.getDirection(), c.getSubject(), c.getMessage(),
                    c.getAuthor() == null ? null : c.getAuthor().getId(),
                    c.getAuthor() == null ? "System" : c.getAuthor().getFullName(),
                    c.getAuthor() != null && c.getAuthor().getRole().isStaff(),
                    c.getCreatedAt(),
                    c.getEditedAt(),
                    openingMessage || !isManual(c.getType()));
        }
    }

    /* --------------------------------------------------- Reply templates */

    public record ReplyTemplateRequest(
            @NotBlank(message = "Give the template a title")
            @Size(max = 120, message = "Title must be 120 characters or fewer") String title,

            /** Leave empty for a template that suits any category. */
            ComplaintCategory category,

            @Size(max = 200) String subject,

            @NotBlank(message = "The template body cannot be empty")
            @Size(max = 4000) String body,

            Boolean active) {
    }

    public record ReplyTemplateView(
            Long id,
            String title,
            ComplaintCategory category,
            String subject,
            String body,
            boolean active,
            int usageCount,
            List<String> placeholders,
            String createdByName,
            Instant createdAt,
            Instant updatedAt) {
    }

    public record NotificationView(
            Long id,
            Long recipientId,
            String recipientName,
            NotificationChannel channel,
            String recipientAddress,
            String subject,
            String body,
            NotificationStatus status,
            String relatedEntity,
            Long relatedId,
            Instant createdAt,
            Instant sentAt) {

        public static NotificationView of(NotificationLog n) {
            return new NotificationView(
                    n.getId(),
                    n.getRecipient() == null ? null : n.getRecipient().getId(),
                    n.getRecipient() == null ? "—" : n.getRecipient().getFullName(),
                    n.getChannel(), n.getRecipientAddress(), n.getSubject(), n.getBody(),
                    n.getStatus(), n.getRelatedEntity(), n.getRelatedId(),
                    n.getCreatedAt(), n.getSentAt());
        }
    }

    /* ------------------------------------------------- Customer 360 view */

    public record CustomerSummaryView(
            Long id,
            String fullName,
            String email,
            String phone,
            Instant joinedAt,
            long totalBookings,
            long activeBookings,
            long cancelledBookings,
            java.math.BigDecimal lifetimeValue,
            java.math.BigDecimal outstandingBalance,
            long openComplaints) {
    }

    public record CustomerProfileView(
            CustomerSummaryView summary,
            List<BookingDtos.BookingView> bookings,
            List<ComplaintView> complaints,
            List<CommunicationView> communications,
            List<NotificationView> notifications) {
    }

    private RelationsDtos() {
    }
}
