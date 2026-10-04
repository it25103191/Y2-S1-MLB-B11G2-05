package com.safari.tms.domain.enums;

public enum Role {
    CUSTOMER("Customer"),
    OPERATIONS_MANAGER("Operations Manager"),
    CUSTOMER_RELATIONS_OFFICER("Customer Relations Officer"),
    SAFARI_VEHICLE_COORDINATOR("Safari Vehicle Coordinator"),
    FINANCE_RESERVATIONS_EXECUTIVE("Finance & Reservations Executive"),
    FINANCE_ACCOUNTS_OFFICER("Finance Accounts Officer");

    private final String label;

    Role(String label) { this.label = label; }

    public String getLabel() { return label; }

    public boolean isStaff() { return this != CUSTOMER; }
}
