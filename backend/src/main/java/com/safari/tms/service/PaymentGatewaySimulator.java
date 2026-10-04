package com.safari.tms.service;

import com.safari.tms.config.AppProperties;
import com.safari.tms.domain.enums.PaymentStatus;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.security.SecureRandom;
import java.util.List;
import java.util.Locale;


@Component
public class PaymentGatewaySimulator {

    private static final Logger log = LoggerFactory.getLogger(PaymentGatewaySimulator.class);


    private static final String SUCCESS_SUFFIX = "4242";
    private static final String DECLINE_SUFFIX = "0002";
    private static final String TIMEOUT_SUFFIX = "0003";

    private static final List<String> DECLINE_REASONS = List.of(
            "Card declined by the issuing bank",
            "Insufficient funds",
            "Card reported lost or stolen",
            "Transaction blocked by fraud screening");

    private final AppProperties props;
    private final SecureRandom random = new SecureRandom();

    public PaymentGatewaySimulator(AppProperties props) {
        this.props = props;
    }

    public record GatewayResult(PaymentStatus status, String reference, String failureReason) {
        public boolean accepted() {
            return status == PaymentStatus.SUCCESS;
        }
    }

    public GatewayResult authorise(String cardNumber) {
        String digits = cardNumber == null ? "" : cardNumber.replaceAll("\\D", "");

        PaymentStatus outcome = forcedOutcome();
        if (outcome == null) {
            if (digits.endsWith(SUCCESS_SUFFIX)) {
                outcome = PaymentStatus.SUCCESS;
            } else if (digits.endsWith(DECLINE_SUFFIX)) {
                outcome = PaymentStatus.DECLINED;
            } else if (digits.endsWith(TIMEOUT_SUFFIX)) {
                outcome = PaymentStatus.TIMEOUT;
            } else {
                outcome = weightedOutcome();
            }
        }

        String reference = "SIMGW-" + Long.toHexString(random.nextLong() & 0xFFFFFFFFL).toUpperCase(Locale.ROOT);

        String reason = switch (outcome) {
            case DECLINED -> DECLINE_REASONS.get(random.nextInt(DECLINE_REASONS.size()));
            case TIMEOUT -> "The payment provider did not respond in time. No money has moved.";
            default -> null;
        };

        log.info("Simulated gateway {} -> {}", reference, outcome);
        return new GatewayResult(outcome, reference, reason);
    }

    private PaymentStatus forcedOutcome() {
        String forced = props.getPayments().getForceOutcome();
        if (forced == null || forced.isBlank()) {
            return null;
        }
        try {
            return PaymentStatus.valueOf(forced.trim().toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException ex) {
            log.warn("Ignoring unknown safari.payments.force-outcome '{}'", forced);
            return null;
        }
    }

    private PaymentStatus weightedOutcome() {
        int success = Math.max(0, props.getPayments().getSuccessRate());
        int decline = Math.max(0, props.getPayments().getDeclineRate());
        int timeout = Math.max(0, props.getPayments().getTimeoutRate());
        int total = success + decline + timeout;
        if (total <= 0) {
            return PaymentStatus.SUCCESS;
        }

        int roll = random.nextInt(total);
        if (roll < success) return PaymentStatus.SUCCESS;
        if (roll < success + decline) return PaymentStatus.DECLINED;
        return PaymentStatus.TIMEOUT;
    }
}
