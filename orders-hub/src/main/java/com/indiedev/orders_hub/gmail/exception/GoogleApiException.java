package com.indiedev.orders_hub.gmail.exception;

public class GoogleApiException extends RuntimeException {

    public GoogleApiException(String message) {
        super(message);
    }

    public GoogleApiException(String message, Throwable cause) {
        super(message, cause);
    }
}
