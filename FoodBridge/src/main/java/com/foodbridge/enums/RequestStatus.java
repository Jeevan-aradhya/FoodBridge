package com.foodbridge.enums;

public enum RequestStatus {
    PENDING, ACCEPTED, REJECTED, CANCELLED, COMPLETED;

    /** The only allowed state changes. Everything else is rejected by the services. */
    public boolean canTransitionTo(RequestStatus next) {
        return switch (this) {
            case PENDING -> next == ACCEPTED || next == REJECTED || next == CANCELLED;
            case ACCEPTED -> next == COMPLETED || next == CANCELLED;
            default -> false;
        };
    }
}
