package com.safari.tms.service.payment;

import com.safari.tms.domain.enums.PaymentMethod;
import org.springframework.stereotype.Component;

/** Concrete strategy: a mobile-money transfer (e.g. eZ Cash, mCash) that finance has confirmed. */
@Component
public class MobileMoneyPayment extends OfflinePayment {

    @Override
    protected PaymentMethod method() {
        return PaymentMethod.MOBILE_MONEY;
    }

    @Override
    protected String label() {
        return "mobile money";
    }
}
