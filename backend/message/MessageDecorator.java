package com.safari.tms.service.message;

/**
 * Decorator pattern: the abstract decorator. Wraps another message and, unless a subclass says
 * otherwise, passes the subject and body straight through.
 */
public abstract class MessageDecorator implements CustomerMessage {

    protected final CustomerMessage wrapped;

    protected MessageDecorator(CustomerMessage wrapped) {
        this.wrapped = wrapped;
    }

    @Override
    public String subject() {
        return wrapped.subject();
    }

    @Override
    public String body() {
        return wrapped.body();
    }
}
