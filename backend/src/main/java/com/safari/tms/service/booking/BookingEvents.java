package com.safari.tms.service.booking;

import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;

/**
 * Observer pattern: the concrete subject. BookingService reports every booking change here, and
 * this class passes it on to each registered observer in turn.
 *
 * <p>Spring finds every {@link BookingObserver} in the application and hands them to the
 * constructor, which registers them. Adding a new reaction to bookings (an SMS, an audit entry, a
 * loyalty point) is therefore just a new observer class; nothing here or in BookingService changes.
 */
@Component
public class BookingEvents implements BookingSubject {

    private final List<BookingObserver> observers = new ArrayList<>();

    public BookingEvents(List<BookingObserver> initialObservers) {
        initialObservers.forEach(this::addObserver);
    }

    @Override
    public void addObserver(BookingObserver observer) {
        if (!observers.contains(observer)) {
            observers.add(observer);
        }
    }

    @Override
    public void removeObserver(BookingObserver observer) {
        observers.remove(observer);
    }

    @Override
    public void notifyObservers(BookingEvent event) {
        for (BookingObserver observer : observers) {
            observer.update(event);
        }
    }
}
