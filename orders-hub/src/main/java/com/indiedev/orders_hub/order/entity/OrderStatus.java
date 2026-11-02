package com.indiedev.orders_hub.order.entity;

public enum OrderStatus {
    UNKNOWN,
    CONFIRMED,
    DISPATCHED,
    SHIPPED,
    OUT_FOR_DELIVERY,
    DELIVERED,
    CANCELLED,
    REFUNDED;

    /** True once nothing more will be delivered for the order. */
    public boolean isFinal() {
        return this == DELIVERED || this == CANCELLED || this == REFUNDED;
    }
}
