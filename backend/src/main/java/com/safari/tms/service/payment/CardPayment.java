package com.safari.tms.service.payment;

import com.safari.tms.domain.Payment;
import com.safari.tms.domain.enums.PaymentMethod;
import com.safari.tms.domain.enums.PaymentStatus;
import com.safari.tms.exception.ApiException;
import com.safari.tms.service.PaymentGatewaySimulator;
import org.springframework.stereotype.Component;

import java.time.Instant;

/**
 * Concrete strategy: a credit or debit card authorised through the (simulated) payment gateway.
 * The only method customers can use on the website, and the only one that can be declined.
 */
@Component
public class CardPayment implements PaymentStrategy {

    private final PaymentGatewaySimulator gateway;

    public CardPayment(PaymentGatewaySimulator gateway) {
        this.gateway = gateway;
    }

    @Override
    public boolean supports(PaymentMethod method) {
        return method == PaymentMethod.CREDIT_CARD || method == PaymentMethod.DEBIT_CARD;
    }

    @Override
    public boolean online() {
        return true;
    }

    @Override
    public void validate(PaymentDetails details) {
        if (digits(details.cardNumber()).length() < 12) {
            throw ApiException.badRequest("Enter the card number to pay by card.");
        }
    }

    @Override
    public PaymentOutcome pay(Payment payment, PaymentDetails details) {
        String digits = digits(details.cardNumber());
        // Only the last four digits are ever stored.
        payment.setCardLast4(digits.length() >= 4 ? digits.substring(digits.length() - 4) : null);
        payment.setCardHolderName(details.cardHolderName());

        PaymentGatewaySimulator.GatewayResult result = gateway.authorise(details.cardNumber());
        payment.setStatus(result.status());
        payment.setGatewayReference(result.reference());
        payment.setFailureReason(result.failureReason());

        if (result.accepted()) {
            payment.setPaidAt(Instant.now());
            return PaymentOutcome.success();
        }
        String message = result.status() == PaymentStatus.DECLINED
                ? result.failureReason() + ". No money has been taken — please try another card."
                : result.failureReason();
        return new PaymentOutcome(false, result.status(), message);
    }

    @Override
    public String successMessage(String paid, String outstanding) {
        return outstanding != null
                ? "Part payment of " + paid + " received. " + outstanding + " still outstanding."
                : "Payment of " + paid + " received in full.";
    }

    @Override
    public String receiptSentence(String paid) {
        return "We have received " + paid + " against your booking.";
    }

    private static String digits(String cardNumber) {
        return cardNumber == null ? "" : cardNumber.replaceAll("\\D", "");
    }
}
