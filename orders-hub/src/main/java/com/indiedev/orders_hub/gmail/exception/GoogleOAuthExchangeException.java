package com.indiedev.orders_hub.gmail.exception;

public class GoogleOAuthExchangeException extends RuntimeException {

    public GoogleOAuthExchangeException(String message) {
        super(message);
    }

    public GoogleOAuthExchangeException(String message, Throwable cause) {
        super(message, cause);
    }
}
