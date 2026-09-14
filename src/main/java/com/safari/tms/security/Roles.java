package com.safari.tms.security;

/** Authority names for use in {@code @PreAuthorize} expressions. */
public final class Roles {

    public static final String CUSTOMER = "CUSTOMER";
    public static final String OPERATIONS_MANAGER = "OPERATIONS_MANAGER";
    public static final String CUSTOMER_RELATIONS_OFFICER = "CUSTOMER_RELATIONS_OFFICER";
    public static final String SAFARI_VEHICLE_COORDINATOR = "SAFARI_VEHICLE_COORDINATOR";
    public static final String FINANCE_RESERVATIONS_EXECUTIVE = "FINANCE_RESERVATIONS_EXECUTIVE";
    public static final String FINANCE_ACCOUNTS_OFFICER = "FINANCE_ACCOUNTS_OFFICER";

    /** Every non-customer role. */
    public static final String ANY_STAFF = "hasAnyRole('" + OPERATIONS_MANAGER + "','"
            + CUSTOMER_RELATIONS_OFFICER + "','" + SAFARI_VEHICLE_COORDINATOR + "','"
            + FINANCE_RESERVATIONS_EXECUTIVE + "','" + FINANCE_ACCOUNTS_OFFICER + "')";

    /** Roles allowed to curate the catalogue (packages and parks). */
    public static final String CATALOGUE_MANAGERS =
            "hasAnyRole('" + OPERATIONS_MANAGER + "','" + FINANCE_RESERVATIONS_EXECUTIVE + "')";

    /** Roles allowed to manage fleet, guides and assignments. */
    public static final String OPERATIONS =
            "hasAnyRole('" + OPERATIONS_MANAGER + "','" + SAFARI_VEHICLE_COORDINATOR + "')";

    /** Roles allowed to handle complaints and customer communication. */
    public static final String RELATIONS =
            "hasAnyRole('" + CUSTOMER_RELATIONS_OFFICER + "','" + OPERATIONS_MANAGER + "')";

    /** Roles allowed to move money. */
    public static final String FINANCE =
            "hasAnyRole('" + FINANCE_ACCOUNTS_OFFICER + "','" + FINANCE_RESERVATIONS_EXECUTIVE + "')";

    private Roles() {
    }
}
