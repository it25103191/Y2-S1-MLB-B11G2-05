package com.safari.tms.service.payment;

import com.safari.tms.domain.Payment;
import com.safari.tms.domain.enums.PaymentMethod;
import com.safari.tms.domain.enums.PaymentStatus;

import java.time.Instant;

/**
 * Shared behaviour for money that finance staff record after receiving it outside the website: it
 * has already changed hands, so it always succeeds and never goes near the card gateway. Each
 * subclass is one concrete strategy (cash, bank transfer, mobile money).
 */
public abstract class OfflinePayment implements PaymentStrategy {

    /** The payment method this strategy records. */
    protected abstract PaymentMethod method();

    /** How the method reads in a sentence, e.g. "bank transfer". */
    protected abstract String label();

    @Override
    public boolean supports(PaymentMethod method) {
        return method == method();
    }

    @Override
    public boolean online() {
        return false;
    }

    @Override
    public PaymentOutcome pay(Payment payment, PaymentDetails details) {
        payment.setStatus(PaymentStatus.SUCCESS);
        payment.setGatewayReference(blankToNull(details.reference()));
        payment.setNotes(blankToNull(details.notes()));
        payment.setPaidAt(Instant.now());
        return PaymentOutcome.success();
    }

    @Override
    public String successMessage(String paid, String outstanding) {
        return "Recorded " + paid + " received by " + label() + "."
                + (outstanding != null ? " " + outstanding + " still outstanding." : "");
    }

    @Override
    public String receiptSentence(String paid) {
        return "We have recorded " + paid + " received by " + label() + ".";
    }

    private static String blankToNull(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }
}
