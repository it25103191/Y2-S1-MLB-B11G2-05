package com.safari.tms.domain.enums;

public enum BookingStatus {
    PENDING, CONFIRMED, COMPLETED, CANCELLED;

    public boolean isActive() { return this == PENDING || this == CONFIRMED; }
}
