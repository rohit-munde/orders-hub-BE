package com.indiedev.orders_hub.exception;

import org.springframework.http.HttpStatus;

public class LastConnectedGoogleAccountException extends BusinessException {

    public LastConnectedGoogleAccountException() {
        super(HttpStatus.CONFLICT, "At least one Gmail account must remain connected");
    }
}
