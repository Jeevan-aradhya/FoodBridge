package com.foodbridge.enums;

public enum DeliveryStatus {
    OPEN, ASSIGNED, PICKED_UP, IN_TRANSIT, DELIVERED, CANCELLED;

    public boolean canTransitionTo(DeliveryStatus next) {
        return switch (this) {
            case OPEN -> next == ASSIGNED || next == CANCELLED;
            case ASSIGNED -> next == PICKED_UP || next == CANCELLED;
            case PICKED_UP -> next == IN_TRANSIT;
            case IN_TRANSIT -> next == DELIVERED;
            default -> false;
        };
    }
}
