package com.safari.tms.service.payment;

import com.safari.tms.domain.enums.PaymentMethod;
import org.springframework.stereotype.Component;

/** Concrete strategy: a bank transfer that finance has seen arrive. */
@Component
public class BankTransferPayment extends OfflinePayment {

    @Override
    protected PaymentMethod method() {
        return PaymentMethod.BANK_TRANSFER;
    }

    @Override
    protected String label() {
        return "bank transfer";
    }
}
