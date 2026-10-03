package com.safari.tms.service.payment;

import com.safari.tms.domain.enums.PaymentStatus;

/**
 * What a strategy reports back after taking a payment.
 *
 * @param failureMessage shown to the payer when the money did not move; {@code null} on success
 */
public record PaymentOutcome(boolean accepted, PaymentStatus status, String failureMessage) {

    public static PaymentOutcome success() {
        return new PaymentOutcome(true, PaymentStatus.SUCCESS, null);
    }
}
