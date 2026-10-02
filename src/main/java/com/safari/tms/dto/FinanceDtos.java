package com.safari.tms.dto;

import com.safari.tms.domain.enums.Currency;
import com.safari.tms.domain.Payment;
import com.safari.tms.domain.Refund;
import com.safari.tms.domain.enums.PaymentMethod;
import com.safari.tms.domain.enums.PaymentStatus;
import com.safari.tms.domain.enums.RefundStatus;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;

public final class FinanceDtos {

    /* ---------------------------------------------------------- Payments */

    public record PaymentRequest(
            @NotNull(message = "Choose a booking") Long bookingId,

            @NotNull(message = "Enter an amount")
            @DecimalMin(value = "0.01", message = "Amount must be greater than zero")
            BigDecimal amount,

            PaymentMethod method,

            /** Mock card details — only the last four digits are ever stored. */
            @Pattern(regexp = "^$|^[0-9 ]{12,25}$", message = "Enter a valid card number")
            String cardNumber,

            @Size(max = 120) String cardHolderName,

            @Pattern(regexp = "^$|^(0[1-9]|1[0-2])/[0-9]{2}$", message = "Use MM/YY")
            String cardExpiry,

            @Pattern(regexp = "^$|^[0-9]{3,4}$", message = "Enter a valid CVC")
            String cardCvc,

            /** Currency shown to the customer; the charge is recorded in it. Defaults to USD. */
            Currency currency,

            /** The amount the customer saw in {@code currency}; checked against the current rate. */
            BigDecimal chargedAmount) {
    }

    public record PaymentView(
            Long id,
            String paymentReference,
            Long bookingId,
            String bookingReference,
            String customerName,
            String customerEmail,
            String packageName,
            LocalDate tripDate,
            BigDecimal amount,
            PaymentMethod method,
            PaymentStatus status,
            String gatewayReference,
            String failureReason,
            String cardLast4,
            String cardHolderName,
            Instant paidAt,
            Instant createdAt,
            String processedByName,

            /* Booking-level context so the finance table needs no second lookup. */
            BigDecimal bookingTotal,
            BigDecimal bookingPaid,
            BigDecimal bookingBalance,
            LocalDate paymentDueDate,
            boolean overdue,
            String bookingStatus,

            String notes,
            String voidReason,
            Instant voidedAt,
            String voidedByName,

            Currency currency,
            BigDecimal chargedAmount,
            BigDecimal fxRate) {

        public static PaymentView of(Payment p) {
            var b = p.getBooking();
            boolean overdue = b.getPaymentDueDate() != null
                    && b.getBalanceDue().compareTo(BigDecimal.ZERO) > 0
                    && b.getStatus() != com.safari.tms.domain.enums.BookingStatus.CANCELLED
                    && b.getPaymentDueDate().isBefore(LocalDate.now());

            return new PaymentView(
                    p.getId(), p.getPaymentReference(),
                    b.getId(), b.getBookingReference(),
                    b.getCustomer().getFullName(), b.getCustomer().getEmail(),
                    b.getSafariPackage().getName(), b.getTripDate(),
                    p.getAmount(), p.getMethod(), p.getStatus(),
                    p.getGatewayReference(), p.getFailureReason(),
                    p.getCardLast4(), p.getCardHolderName(),
                    p.getPaidAt(), p.getCreatedAt(),
                    p.getProcessedBy() == null ? null : p.getProcessedBy().getFullName(),
                    b.getTotalPrice(), b.getAmountPaid(), b.getBalanceDue(),
                    b.getPaymentDueDate(), overdue, b.getStatus().name(),
                    p.getNotes(), p.getVoidReason(), p.getVoidedAt(),
                    p.getVoidedBy() == null ? null : p.getVoidedBy().getFullName(),
                    p.getCurrency() == null ? Currency.USD : p.getCurrency(),
                    p.getChargedAmount() == null ? p.getAmount() : p.getChargedAmount(),
                    p.getFxRate() == null ? BigDecimal.ONE : p.getFxRate());
        }
    }

    /** Cash, bank transfer or mobile money taken by staff; never goes through the card gateway. */
    public record OfflinePaymentRequest(
            @NotNull(message = "Choose a booking") Long bookingId,

            @NotNull(message = "Enter an amount")
            @DecimalMin(value = "0.01", message = "Amount must be greater than zero")
            BigDecimal amount,

            @NotNull(message = "Choose how the money was received") PaymentMethod method,

            @Size(max = 60, message = "Reference must be 60 characters or fewer") String reference,

            @Size(max = 500) String notes,

            /** Currency the money was received in, for example LKR cash at the office. */
            Currency currency,

            BigDecimal chargedAmount) {
    }

    public record VoidPaymentRequest(
            @jakarta.validation.constraints.NotBlank(message = "Give a reason for voiding this payment")
            @Size(max = 500) String reason) {
    }

    /** Result of one attempt against the simulated gateway. */
    public record PaymentResultView(
            boolean accepted,
            PaymentStatus status,
            String message,
            PaymentView payment,
            BookingDtos.BookingView booking,
            boolean bookingNowConfirmed,
            boolean partial,
            BigDecimal remainingBalance) {
    }

    /* ----------------------------------------------------------- Invoice */

    public record InvoiceLineView(String description, int quantity, BigDecimal unitPrice, BigDecimal amount) {
    }

    public record InvoiceView(
            String invoiceNumber,
            Instant issuedAt,
            String customerName,
            String customerEmail,
            String customerPhone,
            String bookingReference,
            String packageName,
            String parkName,
            LocalDate tripDate,
            LocalDate tripEndDate,
            Integer participants,
            List<InvoiceLineView> lines,
            BigDecimal subtotal,
            BigDecimal totalPaid,
            BigDecimal balanceDue,
            boolean fullyPaid,
            LocalDate paymentDueDate,
            boolean overdue,
            String bookingStatus,
            List<PaymentView> payments,
            List<RefundView> refunds) {
    }

    /* ----------------------------------------------------------- Refunds */

    /** What the cancellation policy would award right now, before anything is persisted. */
    public record RefundQuoteView(
            Long bookingId,
            String bookingReference,
            String customerName,
            LocalDate tripDate,
            long daysBeforeTrip,
            BigDecimal amountPaid,
            int refundPercentage,
            BigDecimal refundAmount,
            String policyApplied,
            String policyExplanation,
            boolean eligible,
            boolean alreadyRequested) {
    }

    public record RefundRequestBody(
            @NotNull(message = "Choose a booking") Long bookingId,
            @Size(max = 500) String reason) {
    }

    /** Adjusts a refund that is still waiting for a finance decision. */
    public record RefundUpdateRequest(
            @NotNull(message = "Enter the refund amount")
            @DecimalMin(value = "0.00", message = "Amount cannot be negative")
            BigDecimal amount,

            @Size(max = 500) String reason,

            @Size(max = 500) String decisionNotes) {
    }

    public record RefundDecisionRequest(
            /** Optional override of the policy amount; defaults to the calculated figure. */
            @DecimalMin(value = "0.00", message = "Amount cannot be negative")
            BigDecimal approvedAmount,
            @Size(max = 500) String decisionNotes) {
    }

    public record RefundView(
            Long id,
            String refundReference,
            Long bookingId,
            String bookingReference,
            String customerName,
            String customerEmail,
            String packageName,
            LocalDate tripDate,
            BigDecimal amountPaidAtRequest,
            BigDecimal calculatedAmount,
            BigDecimal adjustedAmount,
            BigDecimal approvedAmount,
            Integer refundPercentage,
            String policyApplied,
            Integer daysBeforeTrip,
            RefundStatus status,
            String reason,
            String decisionNotes,
            Instant requestedAt,
            Instant processedAt,
            String processedByName) {

        public static RefundView of(Refund r) {
            var b = r.getBooking();
            return new RefundView(
                    r.getId(), r.getRefundReference(),
                    b.getId(), b.getBookingReference(),
                    b.getCustomer().getFullName(), b.getCustomer().getEmail(),
                    b.getSafariPackage().getName(), b.getTripDate(),
                    r.getAmountPaidAtRequest(), r.getCalculatedAmount(), r.getAdjustedAmount(),
                    r.getApprovedAmount(),
                    r.getRefundPercentage(), r.getPolicyApplied(), r.getDaysBeforeTrip(),
                    r.getStatus(), r.getReason(), r.getDecisionNotes(),
                    r.getRequestedAt(), r.getProcessedAt(),
                    r.getProcessedBy() == null ? null : r.getProcessedBy().getFullName());
        }
    }

    /* --------------------------------------------------------- Dashboard */

    public record FinanceSummaryView(
            BigDecimal collectedAllTime,
            BigDecimal collectedThisMonth,
            BigDecimal outstanding,
            BigDecimal overdueAmount,
            long overdueBookings,
            long successfulPayments,
            long failedPayments,
            long pendingRefunds,
            BigDecimal refundedAllTime) {
    }

    private FinanceDtos() {
    }
}
