package com.safari.tms.service.payment;

import com.safari.tms.domain.enums.PaymentMethod;
import org.springframework.stereotype.Component;


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
