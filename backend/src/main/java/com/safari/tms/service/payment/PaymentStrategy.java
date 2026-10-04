package com.safari.tms.service.payment;

import com.safari.tms.domain.Payment;
import com.safari.tms.domain.enums.PaymentMethod;


public interface PaymentStrategy {


    boolean supports(PaymentMethod method);


    boolean online();


    default void validate(PaymentDetails details) {
    }

    PaymentOutcome pay(Payment payment, PaymentDetails details);

    String successMessage(String paid, String outstanding);

    String receiptSentence(String paid);
}
