package com.indiedev.orders_hub.exception;

import org.springframework.http.HttpStatus;

public class GmailConnectionRequiredException extends BusinessException {

    public GmailConnectionRequiredException(String message) {
        super(HttpStatus.CONFLICT, message);
    }
}
