package com.safari.tms.service.message;

/** Concrete decorator: signs the message off from a Ceylon Trails team. */
public class SignatureDecorator extends MessageDecorator {

    private final String team;

    public SignatureDecorator(CustomerMessage wrapped, String team) {
        super(wrapped);
        this.team = team;
    }

    @Override
    public String body() {
        return wrapped.body() + "\n\nKind regards,\n" + team + "\nCeylon Trails";
    }
}
