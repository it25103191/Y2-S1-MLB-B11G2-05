package com.safari.tms.service.booking;

/** Observer pattern: the subject keeps a list of observers and notifies them of each change. */
public interface BookingSubject {

    void addObserver(BookingObserver observer);

    void removeObserver(BookingObserver observer);

    void notifyObservers(BookingEvent event);
}
