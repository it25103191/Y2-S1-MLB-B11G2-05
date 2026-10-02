package com.safari.tms.domain.enums;

public enum ComplaintStatus {
    OPEN, IN_PROGRESS, RESOLVED, UNRESOLVED;

    public boolean isClosed() { return this == RESOLVED || this == UNRESOLVED; }
}
