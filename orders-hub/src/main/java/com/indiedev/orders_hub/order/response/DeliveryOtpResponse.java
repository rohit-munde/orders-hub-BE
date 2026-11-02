package com.indiedev.orders_hub.order.response;

import java.time.Instant;

/** A delivery OTP read from the user's Gmail at the moment they asked for it. Never stored. */
public record DeliveryOtpResponse(
        String otp,
        Instant receivedAt
) {
}
