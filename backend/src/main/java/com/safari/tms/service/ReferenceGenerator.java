package com.safari.tms.service;

import java.security.SecureRandom;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;

/**
 * Produces the human-readable reference codes shown on bookings, payments, permits and complaints,
 * e.g. {@code PM-2610-7F48Q}.
 *
 * <p>Singleton pattern: the whole application shares one generator (and one random-number source),
 * so every booking, payment, refund, permit and case reference comes from the same place. The
 * constructor is private, the single instance lives in a static field, and
 * {@link #getInstance()} is the only way to reach it. {@code ReferenceGeneratorConfig} gives that
 * same instance to Spring, so services still receive it through their constructors.
 */
public class ReferenceGenerator {

    /** The one instance, created the first time someone asks for it. */
    private static ReferenceGenerator instance;

    private static final DateTimeFormatter DATE_PART = DateTimeFormatter.ofPattern("yyMM");
    private static final String ALPHABET = "ABCDEFGHJKLMNPQRSTUVWXYZ23456789";
    private final SecureRandom random = new SecureRandom();

    /** Private, so no other class can create a second generator with {@code new}. */
    private ReferenceGenerator() {
    }

    /**
     * Returns the single shared generator, creating it on the first call. {@code synchronized} makes
     * sure two threads starting at the same moment cannot both create one.
     */
    public static synchronized ReferenceGenerator getInstance() {
        if (instance == null) {
            instance = new ReferenceGenerator();
        }
        return instance;
    }

    public String booking() {
        return build("BK");
    }

    public String payment() {
        return build("PY");
    }

    public String refund() {
        return build("RF");
    }

    public String permit() {
        return build("PM");
    }

    public String complaint() {
        return build("CM");
    }

    private String build(String prefix) {
        StringBuilder sb = new StringBuilder(prefix)
                .append('-')
                .append(LocalDate.now().format(DATE_PART))
                .append('-');
        for (int i = 0; i < 5; i++) {
            sb.append(ALPHABET.charAt(random.nextInt(ALPHABET.length())));
        }
        return sb.toString();
    }
}
