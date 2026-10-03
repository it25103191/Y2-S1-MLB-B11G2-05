package com.safari.tms.dto;

import com.safari.tms.domain.Permit;
import com.safari.tms.domain.enums.PermitStatus;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;

public final class PermitDtos {

    public record PermitRequest(
            @NotNull(message = "Choose a booking") Long bookingId,

            /** Defaults to the trip end date plus a short grace period when omitted. */
            LocalDate expiryDate,

            @Size(max = 1000) String notes) {
    }

    public record PermitDecisionRequest(
            LocalDate expiryDate,
            @Size(max = 1000) String notes) {
    }

    public record PermitRenewRequest(
            @NotNull(message = "Choose a new expiry date") LocalDate expiryDate,
            @Size(max = 1000) String notes) {
    }

    public record PermitView(
            Long id,
            String permitNumber,
            Long bookingId,
            String bookingReference,
            String customerName,
            String packageName,
            Long parkId,
            String parkName,
            String permitAuthority,
            PermitStatus status,
            LocalDate issueDate,
            LocalDate expiryDate,
            LocalDate tripDate,
            LocalDate tripEndDate,
            BigDecimal feeAmount,
            Integer coveredParticipants,
            Integer renewalCount,
            String notes,
            String requestedByName,
            Instant approvedAt,
            Instant createdAt,

            /* Derived risk signals, computed server-side so every screen agrees. */
            long daysToExpiry,
            boolean expiringSoon,
            boolean atRisk,
            String riskReason) {

        public static PermitView of(Permit p, int warningDays) {
            LocalDate today = LocalDate.now();
            LocalDate tripStart = p.getBooking().getTripDate();
            LocalDate tripEnd = tripStart.plusDays(
                    Math.max(0, p.getBooking().getSafariPackage().getDurationDays() - 1));

            long days = java.time.temporal.ChronoUnit.DAYS.between(today, p.getExpiryDate());

            boolean expired = p.getStatus() == PermitStatus.EXPIRED
                    || (p.getStatus() == PermitStatus.APPROVED && p.getExpiryDate().isBefore(today));
            boolean soon = p.getStatus() == PermitStatus.APPROVED && !expired && days <= warningDays;

            String reason = null;
            boolean risk = false;

            if (p.getStatus() == PermitStatus.REJECTED) {
                risk = true;
                reason = "Permit was rejected — the trip cannot run without one.";
            } else if (expired) {
                risk = true;
                reason = "Permit expired on " + p.getExpiryDate() + ".";
            } else if (p.getStatus() == PermitStatus.APPROVED && p.getExpiryDate().isBefore(tripEnd)) {
                risk = true;
                reason = "Permit expires " + p.getExpiryDate() + ", before the trip ends on " + tripEnd + ".";
            } else if (p.getStatus() == PermitStatus.PENDING
                    && java.time.temporal.ChronoUnit.DAYS.between(today, tripStart) <= warningDays) {
                risk = true;
                reason = "Still awaiting approval with departure on " + tripStart + ".";
            } else if (soon) {
                reason = "Expires in " + days + " day(s).";
            }

            return new PermitView(
                    p.getId(), p.getPermitNumber(),
                    p.getBooking().getId(), p.getBooking().getBookingReference(),
                    p.getBooking().getCustomer().getFullName(),
                    p.getBooking().getSafariPackage().getName(),
                    p.getPark().getId(), p.getPark().getName(), p.getPark().getPermitAuthority(),
                    expired && p.getStatus() == PermitStatus.APPROVED ? PermitStatus.EXPIRED : p.getStatus(),
                    p.getIssueDate(), p.getExpiryDate(), tripStart, tripEnd,
                    p.getFeeAmount(), p.getCoveredParticipants(), p.getRenewalCount(), p.getNotes(),
                    p.getRequestedBy() == null ? null : p.getRequestedBy().getFullName(),
                    p.getApprovedAt(), p.getCreatedAt(),
                    days, soon, risk, reason);
        }
    }

    /** A booking that has no live permit yet. */
    public record UnpermittedBookingView(
            Long bookingId,
            String bookingReference,
            String customerName,
            String packageName,
            Long parkId,
            String parkName,
            String permitAuthority,
            LocalDate tripDate,
            LocalDate tripEndDate,
            Integer participants,
            BigDecimal estimatedFee,
            long daysToDeparture,
            boolean urgent) {
    }

    public record PermitDashboardView(
            int warningDays,
            List<PermitView> permits,
            List<PermitView> expiringSoon,
            List<PermitView> atRisk,
            List<UnpermittedBookingView> awaitingRequest) {
    }

    private PermitDtos() {
    }
}
