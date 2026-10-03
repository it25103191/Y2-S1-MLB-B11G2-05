package com.safari.tms.service.booking;

import com.safari.tms.domain.Booking;
import com.safari.tms.service.NotificationService;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;

/** Concrete observer: e-mails the customer about changes to their booking. */
@Component
@Order(2)
public class CustomerBookingNotifier implements BookingObserver {

    private final NotificationService notifications;

    public CustomerBookingNotifier(NotificationService notifications) {
        this.notifications = notifications;
    }

    @Override
    public void update(BookingEvent event) {
        Booking b = event.booking();
        String packageName = b.getSafariPackage().getName();

        switch (event.type()) {
            case CREATED -> send(b, "received",
                    "Thanks " + b.getCustomer().getFullName() + ", we are holding " + b.getParticipants()
                            + " place(s) on '" + packageName + "' for " + b.getTripDate()
                            + ". Your balance of " + b.getTotalPrice()
                            + " is due by " + b.getPaymentDueDate() + ".");

            case UPDATED -> send(b, "updated",
                    "Your booking for '" + packageName + "' has changed from " + event.previousTravellers()
                            + " traveller(s) on " + event.previousTripDate() + " to " + b.getParticipants()
                            + " traveller(s) on " + b.getTripDate() + ". The new total is " + b.getTotalPrice()
                            + ", due by " + b.getPaymentDueDate() + ".");

            case CANCELLED -> send(b, "cancelled",
                    "Your booking for '" + packageName + "' on " + b.getTripDate() + " has been cancelled. "
                            + (b.getAmountPaid().compareTo(BigDecimal.ZERO) > 0
                            ? "Any refund due will be assessed against our cancellation policy."
                            : "No payment had been taken."));

            case REMOVED -> send(b, "removed",
                    "Your unpaid booking for '" + packageName + "' has been removed and its seats released.");

            // Status changes made in the staff console are not e-mailed.
            case STATUS_CHANGED -> { }
        }
    }

    private void send(Booking b, String what, String body) {
        notifications.email(b.getCustomer(), "Booking " + b.getBookingReference() + " " + what, body,
                "Booking", b.getId());
    }
}
