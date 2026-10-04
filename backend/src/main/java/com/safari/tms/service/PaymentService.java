package com.safari.tms.service;

import com.safari.tms.domain.Booking;
import com.safari.tms.domain.Payment;
import com.safari.tms.domain.User;
import com.safari.tms.domain.enums.BookingStatus;
import com.safari.tms.domain.enums.Currency;
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
import com.safari.tms.service.booking.BookingEvent;
import com.safari.tms.service.booking.BookingEvents;
import com.safari.tms.service.payment.PaymentDetails;
import com.safari.tms.service.payment.PaymentOutcome;
import com.safari.tms.service.payment.PaymentStrategy;
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
    private final List<PaymentStrategy> strategies;
    private final ReferenceGenerator references;
    private final NotificationService notifications;
    private final CurrencyService currency;
    private final BookingEvents bookingEvents;

    public PaymentService(PaymentRepository payments,
                          RefundRepository refunds,
                          BookingRepository bookings,
                          List<PaymentStrategy> strategies,
                          ReferenceGenerator references,
                          NotificationService notifications,
                          CurrencyService currency,
                          BookingEvents bookingEvents) {
        this.payments = payments;
        this.refunds = refunds;
        this.bookings = bookings;
        this.strategies = strategies;
        this.references = references;
        this.notifications = notifications;
        this.currency = currency;
        this.bookingEvents = bookingEvents;
    }



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


    @Transactional
    public PaymentResultView process(PaymentRequest request, User caller) {
        Booking booking = requireVisible(request.bookingId(), caller);
        BigDecimal amount = validateChargeable(booking, request.amount());

        PaymentMethod method = request.method() == null ? PaymentMethod.CREDIT_CARD : request.method();
        PaymentStrategy strategy = strategyFor(method);


        if (!strategy.online()) {
            throw ApiException.badRequest("Online payments must be made by card. Cash, bank transfer and"
                    + " mobile money are recorded by our finance team.");
        }
        return take(booking, amount, method, strategy,
                PaymentDetails.card(request.cardNumber(), request.cardHolderName()),
                request.currency(), request.chargedAmount(), caller);
    }



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




    @Transactional
    public PaymentResultView recordOffline(OfflinePaymentRequest request, User staff) {
        PaymentStrategy strategy = strategyFor(request.method());
        if (strategy.online()) {
            throw ApiException.badRequest("Card payments go through the payment gateway. Record only cash,"
                    + " bank transfer or mobile money here.");
        }

        Booking booking = bookings.findDetailById(request.bookingId())
                .orElseThrow(() -> ApiException.notFound("Booking", request.bookingId()));
        BigDecimal amount = validateChargeable(booking, request.amount());

        return take(booking, amount, request.method(), strategy,
                PaymentDetails.recorded(request.reference(), request.notes()),
                request.currency(), request.chargedAmount(), staff);
    }


    private PaymentResultView take(Booking booking, BigDecimal amount, PaymentMethod method,
                                   PaymentStrategy strategy, PaymentDetails details,
                                   Currency chargeCurrency, BigDecimal chargedAmount, User actor) {
        strategy.validate(details);

        Payment payment = new Payment();
        payment.setPaymentReference(references.payment());
        payment.setBooking(booking);
        payment.setAmount(amount);
        payment.setMethod(method);
        payment.setProcessedBy(actor);
        applyCharge(payment, currency.charge(amount, chargeCurrency, chargedAmount));

        PaymentOutcome outcome = strategy.pay(payment, details);
        boolean confirmedNow = outcome.accepted() && applySettlement(booking, amount);
        Payment saved = payments.save(payment);
        if (confirmedNow) {
            bookingEvents.notifyObservers(BookingEvent.statusChanged(booking, actor, BookingStatus.PENDING,
                    "Paid in full (" + saved.getPaymentReference() + ")"));
        }

        String message = outcome.failureMessage();
        if (outcome.accepted()) {
            BigDecimal remaining = booking.getBalanceDue();
            String paid = charged(payment);
            String outstanding = remaining.compareTo(BigDecimal.ZERO) > 0 ? inCurrency(remaining, payment) : null;
            message = strategy.successMessage(paid, outstanding);

            notifications.email(booking.getCustomer(),
                    "Payment received for " + booking.getBookingReference(),
                    strategy.receiptSentence(paid) + " "
                            + (outstanding != null
                            ? "Outstanding balance: " + outstanding + "."
                            : "Your booking is now confirmed in full."),
                    "Payment", saved.getId());
        }

        Booking fresh = bookings.findDetailById(booking.getId()).orElse(booking);
        return new PaymentResultView(
                outcome.accepted(), outcome.status(), message,
                PaymentView.of(payments.findDetailById(saved.getId()).orElse(saved)),
                BookingDtos.BookingView.of(fresh),
                confirmedNow,
                outcome.accepted() && fresh.getBalanceDue().compareTo(BigDecimal.ZERO) > 0,
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
        if (reopened) {
            bookingEvents.notifyObservers(BookingEvent.statusChanged(booking, staff, BookingStatus.CONFIRMED,
                    "Payment " + payment.getPaymentReference() + " was voided"));
        }

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

    /** Picks the strategy for a payment method. */
    private PaymentStrategy strategyFor(PaymentMethod method) {
        return strategies.stream()
                .filter(st -> st.supports(method))
                .findFirst()
                .orElseThrow(() -> ApiException.badRequest("Payments by " + method + " are not supported."));
    }

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

    /** Checks the booking can take this amount and returns it rounded to cents. */
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
