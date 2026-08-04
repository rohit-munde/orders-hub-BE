package com.indiedev.orders_hub.gmail.exception;

public class GmailConnectionRequiredException extends RuntimeException {

    public GmailConnectionRequiredException(String message) {
        super(message);
    }
}
