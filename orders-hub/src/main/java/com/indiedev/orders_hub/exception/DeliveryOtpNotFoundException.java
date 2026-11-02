package com.indiedev.orders_hub.exception;

import org.springframework.http.HttpStatus;

public class DeliveryOtpNotFoundException extends BusinessException {
    public DeliveryOtpNotFoundException(String message) {
        super(HttpStatus.NOT_FOUND, message);
    }
}
