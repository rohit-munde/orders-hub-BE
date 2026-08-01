package com.indiedev.orders_hub.gmail.exception;

public class ConnectedAccountConflictException extends RuntimeException {

    public ConnectedAccountConflictException() {
        super("This Gmail account is already connected to another user");
    }
}
