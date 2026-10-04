package com.safari.tms.service.message;

/**
 * Concrete decorator: makes sure the case reference is in the subject and adds it at the end of
 * the message, so the customer can quote it when they reply.
 */
public class CaseReferenceDecorator extends MessageDecorator {

    private final String caseReference;

    public CaseReferenceDecorator(CustomerMessage wrapped, String caseReference) {
        super(wrapped);
        this.caseReference = caseReference;
    }

    @Override
    public String subject() {
        String subject = wrapped.subject();
        return subject.contains(caseReference) ? subject : subject + " (" + caseReference + ")";
    }

    @Override
    public String body() {
        return wrapped.body() + "\n\nCase reference: " + caseReference;
    }
}
