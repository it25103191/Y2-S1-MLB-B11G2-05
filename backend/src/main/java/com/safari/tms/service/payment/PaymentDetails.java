package com.safari.tms.service.payment;


public record PaymentDetails(String cardNumber, String cardHolderName, String reference, String notes) {

    public static PaymentDetails card(String cardNumber, String cardHolderName) {
        return new PaymentDetails(cardNumber, cardHolderName, null, null);
    }

    public static PaymentDetails recorded(String reference, String notes) {
        return new PaymentDetails(null, null, reference, notes);
    }
}
