package com.indiedev.orders_hub.exception;

import org.springframework.http.HttpStatus;

public class GoogleApiException extends BusinessException {

    public GoogleApiException(String message) {
        super(HttpStatus.BAD_GATEWAY, message);
    }

    public GoogleApiException(String message, Throwable cause) {
        super(HttpStatus.BAD_GATEWAY, message, cause);
    }
}
