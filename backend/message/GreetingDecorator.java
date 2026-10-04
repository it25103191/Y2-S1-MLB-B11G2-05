package com.safari.tms.service.message;

/** Concrete decorator: opens the message with "Dear Sarah," using the customer's first name. */
public class GreetingDecorator extends MessageDecorator {

    private final String fullName;

    public GreetingDecorator(CustomerMessage wrapped, String fullName) {
        super(wrapped);
        this.fullName = fullName;
    }

    @Override
    public String body() {
        String first = fullName == null || fullName.isBlank() ? "customer" : fullName.trim().split("\\s+")[0];
        return "Dear " + first + ",\n\n" + wrapped.body();
    }
}
