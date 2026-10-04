package com.safari.tms.service.payment;

import com.safari.tms.domain.enums.PaymentStatus;


public record PaymentOutcome(boolean accepted, PaymentStatus status, String failureMessage) {

    public static PaymentOutcome success() {
        return new PaymentOutcome(true, PaymentStatus.SUCCESS, null);
    }
}
