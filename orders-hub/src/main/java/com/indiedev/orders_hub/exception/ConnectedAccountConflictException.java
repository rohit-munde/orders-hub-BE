package com.indiedev.orders_hub.exception;

import org.springframework.http.HttpStatus;

public class ConnectedAccountConflictException extends BusinessException {

    public ConnectedAccountConflictException() {
        super(HttpStatus.CONFLICT, "This Gmail account is already connected to another user");
    }
}
