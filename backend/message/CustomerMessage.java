package com.safari.tms.service.message;

/**
 * Decorator pattern: the component. Any message we e-mail to a customer about their case.
 *
 * <p>A {@link PlainMessage} holds just the text. Decorators wrap it to add a greeting, the case
 * reference or a signature, and can be combined in any order, so each e-mail gets exactly the
 * extras it needs without a separate class for every combination.
 */
public interface CustomerMessage {

    String subject();

    String body();
}
