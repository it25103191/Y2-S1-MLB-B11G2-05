package com.safari.tms.service.payment;

import com.safari.tms.domain.enums.PaymentMethod;
import org.springframework.stereotype.Component;


@Component
public class CashPayment extends OfflinePayment {

    @Override
    protected PaymentMethod method() {
        return PaymentMethod.CASH;
    }

    @Override
    protected String label() {
        return "cash";
    }
}
