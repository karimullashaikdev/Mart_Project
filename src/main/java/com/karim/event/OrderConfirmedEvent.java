package com.karim.event;

import java.util.UUID;

import org.springframework.context.ApplicationEvent;

/**
 * Published immediately after an order is confirmed (PENDING → CONFIRMED).
 * Consumed by OrderProcessingScheduler to auto-move the order to PROCESSING
 * after 5 minutes.
 */
public class OrderConfirmedEvent extends ApplicationEvent {

    private final UUID orderId;

    public OrderConfirmedEvent(Object source, UUID orderId) {
        super(source);
        this.orderId = orderId;
    }

    public UUID getOrderId() {
        return orderId;
    }
}