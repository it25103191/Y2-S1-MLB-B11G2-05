package com.safari.tms.service;

import com.safari.tms.domain.Booking;
import com.safari.tms.domain.Payment;
import com.safari.tms.domain.User;
import com.safari.tms.domain.enums.BookingStatus;
import com.safari.tms.domain.enums.PaymentMethod;
import com.safari.tms.domain.enums.PaymentStatus;
import com.safari.tms.domain.enums.RefundStatus;
import com.safari.tms.domain.enums.Role;
import com.safari.tms.dto.BookingDtos;
import com.safari.tms.dto.FinanceDtos.*;
import com.safari.tms.exception.ApiException;
import com.safari.tms.repo.BookingRepository;
import com.safari.tms.repo.PaymentRepository;
import com.safari.tms.repo.RefundRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.time.YearMonth;
import java.time.ZoneOffset;
import java.util.List;

@Service
public class PaymentService {

    private final PaymentRepository payments;
    private final RefundRepository refunds;
    private final BookingRepository bookings;
    private final PaymentGatewaySimulator gateway;
    private final ReferenceGenerator references;
    private final NotificationService notifications;
    private final CurrencyService currency;

    public PaymentService(PaymentRepository payments,
                          RefundRepository refunds,
                          BookingRepository bookings,
                          PaymentGatewaySimulator gateway,
                          ReferenceGenerator references,
                          NotificationService notifications,
                          CurrencyService currency) {
        this.payments = payments;
        this.refunds = refunds;
        this.bookings = bookings;
        this.gateway = gateway;
        this.references = references;
        this.notifications = notifications;
        this.currency = currency;
    }

    /* ------------------------------------------------------------ Queries */

    @Transactional(readOnly = true)
    public List<PaymentView> findAll() {
        return payments.findAllDetailed().stream().map(PaymentView::of).toList();
    }

    @Transactional(readOnly = true)
    public List<PaymentView> findForBooking(Long bookingId, User caller) {
        Booking booking = requireVisible(bookingId, caller);
        return payments.findForBooking(booking.getId()).stream().map(PaymentView::of).toList();
    }

    @Transactional(readOnly = true)
    public PaymentView findOne(Long id) {
        return PaymentView.of(payments.findDetailById(id)
                .orElseThrow(() -> ApiException.notFound("Payment", id)));
    }

    /* ------------------------------------------------------------ Process */

    /**
     * Takes a payment against a booking through the simulated gateway.
     *
     * <p>Partial payments are allowed: any amount up to the outstanding balance is accepted. The
     * booking is only moved to CONFIRMED when the balance reaches zero.
     */
    @Transactional
    public PaymentResultView process(PaymentRequest request, User caller) {
        Booking booking = requireVisible(request.bookingId(), caller);
        BigDecimal amount = validateChargeable(booking, request.amount());

        String digits = request.cardNumber() == null ? "" : request.cardNumber().replaceAll("\\D", "");
        PaymentMethod method = request.method() == null ? PaymentMethod.CREDIT_CARD : request.method();

        // Only cards go through the gateway. Offline money is recorded by finance staff instead,
        // which is what stops a cash payment from being "declined" by the card simulator.
        if (!isCard(method)) {
            throw ApiException.badRequest("Online payments must be made by card. Cash, bank transfer and"
                    + " mobile money are recorded by our finance team.");
        }
        if (digits.length() < 12) {
            throw ApiException.badRequest("Enter the card number to pay by card.");
        }

        Payment payment = new Payment();
        payment.setPaymentReference(references.payment());
        payment.setBooking(booking);
        payment.setAmount(amount);
        payment.setMethod(method);
        payment.setCardLast4(digits.length() >= 4 ? digits.substring(digits.length() - 4) : null);
        payment.setCardHolderName(request.cardHolderName());
        payment.setProcessedBy(caller);
        applyCharge(payment, currency.charge(amount, request.currency(), request.chargedAmount()));

        PaymentGatewaySimulator.GatewayResult result = gateway.authorise(request.cardNumber());
        payment.setStatus(result.status());
        payment.setGatewayReference(result.reference());
        payment.setFailureReason(result.failureReason());

        boolean confirmedNow = false;

        if (result.accepted()) {
            payment.setPaidAt(Instant.now());
            confirmedNow = applySettlement(booking, amount);
        }

        Payment saved = payments.save(payment);

        String message;
        if (result.accepted()) {
            BigDecimal remaining = booking.getBalanceDue();
            String paid = charged(payment);
            String left = inCurrency(remaining, payment);
            message = remaining.compareTo(BigDecimal.ZERO) > 0
                    ? "Part payment of " + paid + " received. " + left + " still outstanding."
                    : "Payment of " + paid + " received in full.";

            notifications.email(booking.getCustomer(),
                    "Payment received for " + booking.getBookingReference(),
                    "We have received " + paid + " against your booking. "
                            + (remaining.compareTo(BigDecimal.ZERO) > 0
                            ? "Outstanding balance: " + left + "."
                            : "Your booking is now confirmed in full."),
                    "Payment", saved.getId());
        } else if (result.status() == PaymentStatus.DECLINED) {
            message = result.failureReason() + ". No money has been taken — please try another card.";
        } else {
            message = result.failureReason();
        }

        Booking fresh = bookings.findDetailById(booking.getId()).orElse(booking);

        return new PaymentResultView(
                result.accepted(), result.status(), message,
                PaymentView.of(payments.findDetailById(saved.getId()).orElse(saved)),
                BookingDtos.BookingView.of(fresh),
                confirmedNow,
                result.accepted() && fresh.getBalanceDue().compareTo(BigDecimal.ZERO) > 0,
                fresh.getBalanceDue());
    }

    /* ------------------------------------------------------------ Invoice */

    @Transactional(readOnly = true)
    public InvoiceView invoice(Long bookingId, User caller) {
        Booking b = requireVisible(bookingId, caller);

        LocalDate tripEnd = b.getTripDate()
                .plusDays(Math.max(0, b.getSafariPackage().getDurationDays() - 1));

        List<InvoiceLineView> lines = List.of(new InvoiceLineView(
                b.getSafariPackage().getName() + " — " + b.getSafariPackage().getPark().getName()
                        + " (" + b.getSafariPackage().getDurationDays() + " days)",
                b.getParticipants(),
                b.getSafariPackage().getPricePerPerson(),
                b.getTotalPrice()));

        boolean overdue = b.getPaymentDueDate() != null
                && b.getBalanceDue().compareTo(BigDecimal.ZERO) > 0
                && b.getStatus() != BookingStatus.CANCELLED
                && b.getPaymentDueDate().isBefore(LocalDate.now());

        return new InvoiceView(
                "INV-" + b.getBookingReference(),
                b.getCreatedAt(),
                b.getCustomer().getFullName(), b.getCustomer().getEmail(), b.getCustomer().getPhone(),
                b.getBookingReference(), b.getSafariPackage().getName(),
                b.getSafariPackage().getPark().getName(),
                b.getTripDate(), tripEnd, b.getParticipants(),
                lines, b.getTotalPrice(), b.getAmountPaid(), b.getBalanceDue(),
                b.isFullyPaid(), b.getPaymentDueDate(), overdue, b.getStatus().name(),
                payments.findForBooking(b.getId()).stream().map(PaymentView::of).toList(),
                refunds.findForBooking(b.getId()).stream().map(RefundView::of).toList());
    }

    /* ---------------------------------------------------------- Dashboard */

    @Transactional(readOnly = true)
    public FinanceSummaryView summary() {
        List<Payment> all = payments.findAllDetailed();

        BigDecimal collected = all.stream()
                .filter(p -> p.getStatus() == PaymentStatus.SUCCESS)
                .map(Payment::getAmount)
                .reduce(BigDecimal.ZERO, BigDecimal::add);

        YearMonth thisMonth = YearMonth.now();
        BigDecimal thisMonthTotal = all.stream()
                .filter(p -> p.getStatus() == PaymentStatus.SUCCESS && p.getPaidAt() != null)
                .filter(p -> YearMonth.from(p.getPaidAt().atZone(ZoneOffset.UTC)).equals(thisMonth))
                .map(Payment::getAmount)
                .reduce(BigDecimal.ZERO, BigDecimal::add);

        List<Booking> owing = bookings.findWithOutstandingBalance();
        BigDecimal outstanding = owing.stream()
                .map(Booking::getBalanceDue).reduce(BigDecimal.ZERO, BigDecimal::add);

        LocalDate today = LocalDate.now();
        List<Booking> overdue = owing.stream()
                .filter(b -> b.getPaymentDueDate() != null && b.getPaymentDueDate().isBefore(today))
                .toList();
        BigDecimal overdueAmount = overdue.stream()
                .map(Booking::getBalanceDue).reduce(BigDecimal.ZERO, BigDecimal::add);

        BigDecimal refunded = refunds.findAllDetailed().stream()
                .filter(r -> r.getStatus() == com.safari.tms.domain.enums.RefundStatus.PROCESSED)
                .map(r -> r.getApprovedAmount() == null ? BigDecimal.ZERO : r.getApprovedAmount())
                .reduce(BigDecimal.ZERO, BigDecimal::add);

        return new FinanceSummaryView(
                collected, thisMonthTotal, outstanding, overdueAmount, overdue.size(),
                all.stream().filter(p -> p.getStatus() == PaymentStatus.SUCCESS).count(),
                all.stream().filter(p -> p.getStatus() == PaymentStatus.DECLINED
                        || p.getStatus() == PaymentStatus.TIMEOUT).count(),
                refunds.findAllDetailed().stream()
                        .filter(r -> r.getStatus() == com.safari.tms.domain.enums.RefundStatus.REQUESTED)
                        .count(),
                refunded);
    }

    /* ------------------------------------------------------------ Helpers */

    /* ---------------------------------------------------- Offline payment */

    /** Records cash, bank transfer or mobile money taken by staff. Never touches the card gateway. */
    @Transactional
    public PaymentResultView recordOffline(OfflinePaymentRequest request, User staff) {
        if (isCard(request.method())) {
            throw ApiException.badRequest("Card payments go through the payment gateway. Record only cash,"
                    + " bank transfer or mobile money here.");
        }

        Booking booking = bookings.findDetailById(request.bookingId())
                .orElseThrow(() -> ApiException.notFound("Booking", request.bookingId()));
        BigDecimal amount = validateChargeable(booking, request.amount());

        Payment payment = new Payment();
        payment.setPaymentReference(references.payment());
        payment.setBooking(booking);
        payment.setAmount(amount);
        payment.setMethod(request.method());
        payment.setStatus(PaymentStatus.SUCCESS);
        payment.setGatewayReference(request.reference() == null || request.reference().isBlank()
                ? null : request.reference().trim());
        payment.setNotes(request.notes() == null || request.notes().isBlank() ? null : request.notes().trim());
        payment.setPaidAt(Instant.now());
        payment.setProcessedBy(staff);
        applyCharge(payment, currency.charge(amount, request.currency(), request.chargedAmount()));

        boolean confirmedNow = applySettlement(booking, amount);
        Payment saved = payments.save(payment);

        BigDecimal remaining = booking.getBalanceDue();
        String methodLabel = request.method().name().toLowerCase().replace('_', ' ');
        String paid = charged(payment);
        String left = inCurrency(remaining, payment);
        notifications.email(booking.getCustomer(),
                "Payment received for " + booking.getBookingReference(),
                "We have recorded " + paid + " received by " + methodLabel + ". "
                        + (remaining.compareTo(BigDecimal.ZERO) > 0
                        ? "Outstanding balance: " + left + "."
                        : "Your booking is now confirmed in full."),
                "Payment", saved.getId());

        Booking fresh = bookings.findDetailById(booking.getId()).orElse(booking);
        return new PaymentResultView(
                true, PaymentStatus.SUCCESS,
                "Recorded " + paid + " received by " + methodLabel + "."
                        + (remaining.compareTo(BigDecimal.ZERO) > 0 ? " " + left + " still outstanding." : ""),
                PaymentView.of(payments.findDetailById(saved.getId()).orElse(saved)),
                BookingDtos.BookingView.of(fresh),
                confirmedNow,
                fresh.getBalanceDue().compareTo(BigDecimal.ZERO) > 0,
                fresh.getBalanceDue());
    }

    /* --------------------------------------------------------------- Void */

    /**
     * Voids a successful payment: the record is kept but marked VOIDED, the money comes off the
     * booking, and a confirmed booking drops back to pending until the balance is paid again.
     */
    @Transactional
    public PaymentView voidPayment(Long id, String reason, User staff) {
        Payment payment = payments.findDetailById(id)
                .orElseThrow(() -> ApiException.notFound("Payment", id));

        if (payment.getStatus() != PaymentStatus.SUCCESS) {
            throw ApiException.badRequest("Only successful payments can be voided. This one is "
                    + payment.getStatus().name().toLowerCase() + ".");
        }

        Booking booking = payment.getBooking();
        if (refunds.existsByBookingIdAndStatusIn(booking.getId(),
                List.of(RefundStatus.REQUESTED, RefundStatus.APPROVED))) {
            throw ApiException.conflict("A refund is in progress for booking " + booking.getBookingReference()
                    + ". Finish or withdraw it before voiding payments.");
        }
        if (payment.getAmount().compareTo(booking.getAmountPaid()) > 0) {
            throw ApiException.conflict("Only " + booking.getAmountPaid() + " is still held against booking "
                    + booking.getBookingReference() + ". Part of this payment has already been refunded,"
                    + " so it can't be voided.");
        }

        payment.setStatus(PaymentStatus.VOIDED);
        payment.setVoidReason(reason.trim());
        payment.setVoidedAt(Instant.now());
        payment.setVoidedBy(staff);

        booking.setAmountPaid(booking.getAmountPaid().subtract(payment.getAmount()));
        boolean reopened = false;
        if (booking.getStatus() == BookingStatus.CONFIRMED && !booking.isFullyPaid()) {
            booking.setStatus(BookingStatus.PENDING);
            reopened = true;
        }
        bookings.save(booking);
        payments.save(payment);

        notifications.email(booking.getCustomer(),
                "Payment voided on " + booking.getBookingReference(),
                "A payment of " + payment.getAmount() + " on booking " + booking.getBookingReference()
                        + " has been voided: " + payment.getVoidReason() + ". Outstanding balance: "
                        + booking.getBalanceDue() + "."
                        + (reopened ? " The booking is pending again until the balance is paid." : ""),
                "Payment", payment.getId());

        return PaymentView.of(payments.findDetailById(id).orElse(payment));
    }

    /* ------------------------------------------------------------- Delete */

    /** Removes a failed attempt. Declined and timed-out payments never moved money. */
    @Transactional
    public void deleteFailed(Long id) {
        Payment payment = payments.findById(id).orElseThrow(() -> ApiException.notFound("Payment", id));
        if (payment.getStatus() != PaymentStatus.DECLINED && payment.getStatus() != PaymentStatus.TIMEOUT) {
            throw ApiException.badRequest("Only declined or timed-out attempts can be deleted, because they"
                    + " moved no money. Void a successful payment instead.");
        }
        payments.delete(payment);
    }

    /* ------------------------------------------------------------ Helpers */

    private static boolean isCard(PaymentMethod method) {
        return method == PaymentMethod.CREDIT_CARD || method == PaymentMethod.DEBIT_CARD;
    }

    /** Checks the booking can take this amount and returns it rounded to cents. */
    /** "LKR 414,000.00" or "USD 1,380.00": what the customer actually handed over. */
    private static String charged(Payment payment) {
        String code = payment.getCurrency() == null ? "USD" : payment.getCurrency().name();
        BigDecimal value = payment.getChargedAmount() == null ? payment.getAmount() : payment.getChargedAmount();
        return code + " " + String.format(java.util.Locale.US, "%,.2f", value);
    }

    /** A USD ledger figure expressed in the currency and at the rate this payment used. */
    private static String inCurrency(BigDecimal usd, Payment payment) {
        String code = payment.getCurrency() == null ? "USD" : payment.getCurrency().name();
        BigDecimal rate = payment.getFxRate() == null ? BigDecimal.ONE : payment.getFxRate();
        return code + " " + String.format(java.util.Locale.US, "%,.2f", usd.multiply(rate));
    }

    private static void applyCharge(Payment payment, com.safari.tms.dto.CurrencyDtos.Charge charge) {
        payment.setCurrency(charge.currency());
        payment.setChargedAmount(charge.chargedAmount());
        payment.setFxRate(charge.fxRate());
    }

    private BigDecimal validateChargeable(Booking booking, BigDecimal requested) {
        if (booking.getStatus() == BookingStatus.CANCELLED) {
            throw ApiException.badRequest("Booking " + booking.getBookingReference()
                    + " is cancelled and cannot take payment.");
        }

        BigDecimal balance = booking.getBalanceDue();
        if (balance.compareTo(BigDecimal.ZERO) <= 0) {
            throw ApiException.badRequest("Booking " + booking.getBookingReference()
                    + " is already paid in full.");
        }

        BigDecimal amount = requested.setScale(2, java.math.RoundingMode.HALF_UP);
        if (amount.compareTo(balance) > 0) {
            throw ApiException.badRequest("That is more than the outstanding balance of "
                    + balance + ". Enter " + balance + " or less.");
        }
        return amount;
    }

    /** Adds settled money to the booking; returns true if that confirmed it. */
    private boolean applySettlement(Booking booking, BigDecimal amount) {
        booking.setAmountPaid(booking.getAmountPaid().add(amount));
        boolean confirmedNow = false;
        if (booking.isFullyPaid() && booking.getStatus() == BookingStatus.PENDING) {
            booking.setStatus(BookingStatus.CONFIRMED);
            confirmedNow = true;
        }
        bookings.save(booking);
        return confirmedNow;
    }

    private Booking requireVisible(Long bookingId, User caller) {
        Booking booking = bookings.findDetailById(bookingId)
                .orElseThrow(() -> ApiException.notFound("Booking", bookingId));
        if (caller.getRole() == Role.CUSTOMER && !booking.getCustomer().getId().equals(caller.getId())) {
            throw ApiException.forbidden("That booking belongs to another customer.");
        }
        return booking;
    }
}
