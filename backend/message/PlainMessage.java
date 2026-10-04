package com.safari.tms.service.message;

/** Decorator pattern: the concrete component. The bare subject and text, with nothing added. */
public record PlainMessage(String subject, String body) implements CustomerMessage {
}
