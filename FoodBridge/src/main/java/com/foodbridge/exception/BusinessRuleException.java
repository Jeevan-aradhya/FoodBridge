package com.foodbridge.exception;

/** Thrown when a request breaks a business rule (e.g. not enough quantity left). Maps to HTTP 409. */
public class BusinessRuleException extends RuntimeException {
    public BusinessRuleException(String message) { super(message); }
}
