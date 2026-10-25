package com.foodbridge.exception;

/** Thrown when an entity does not exist (or must not be visible). Maps to HTTP 404. */
public class ResourceNotFoundException extends RuntimeException {
    public ResourceNotFoundException(String message) { super(message); }
}
