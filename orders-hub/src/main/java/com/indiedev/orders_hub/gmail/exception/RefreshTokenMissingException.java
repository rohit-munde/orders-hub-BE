package com.indiedev.orders_hub.gmail.exception;

public class RefreshTokenMissingException extends RuntimeException {

    public RefreshTokenMissingException() {
        super("Google did not return a refresh token; revoke access and reconnect with offline consent");
    }
}
