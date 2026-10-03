package com.safari.tms.service.booking;

import com.safari.tms.service.RefundService;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

/**
 * Concrete observer: when a booking that had money against it is cancelled, raise a refund request
 * worked out from the cancellation policy. Runs first, before the customer is e-mailed.
 */
@Component
@Order(1)
public class RefundOnCancellation implements BookingObserver {

    private final RefundService refundService;

    public RefundOnCancellation(RefundService refundService) {
        this.refundService = refundService;
    }

    @Override
    public void update(BookingEvent event) {
        if (!event.isCancellation()) {
            return;
        }
        String reason = event.type() == BookingEvent.Type.CANCELLED
                ? "Booking cancelled: " + event.booking().getCancellationReason()
                : "Booking cancelled by staff";
        refundService.createFor(event.booking(), reason, event.actor());
    }
}
