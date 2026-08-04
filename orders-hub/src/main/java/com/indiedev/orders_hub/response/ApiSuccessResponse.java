package com.indiedev.orders_hub.response;

public record ApiSuccessResponse<T>(
        boolean success,
        String message,
        T payload
) {
    public ApiSuccessResponse(String message, T payload) {
        this(true, message, payload);
    }
}
