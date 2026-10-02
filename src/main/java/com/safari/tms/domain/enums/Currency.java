package com.safari.tms.domain.enums;

/**
 * Currencies a customer can see prices and pay in. USD is the ledger currency: every booking,
 * payment and refund amount is stored in USD, and LKR is converted at the current exchange rate.
 */
public enum Currency {
    USD,
    LKR
}
