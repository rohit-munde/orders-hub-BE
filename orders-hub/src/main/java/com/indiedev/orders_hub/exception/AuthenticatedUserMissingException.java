package com.indiedev.orders_hub.exception;

import org.springframework.http.HttpStatus;

public class AuthenticatedUserMissingException extends BusinessException {
    public AuthenticatedUserMissingException() {
        super(HttpStatus.UNAUTHORIZED, "Authenticated user ID is missing");
    }
}
