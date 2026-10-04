package com.safari.tms.service.booking;

/**
 * Observer pattern: anything that wants to react when a booking changes.
 *
 * <p>Each observer is told about every {@link BookingEvent} and decides for itself whether it
 * cares. BookingService does not know which observers exist or what they do.
 */
public interface BookingObserver {

    void update(BookingEvent event);
}
