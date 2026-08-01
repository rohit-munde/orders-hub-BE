package com.indiedev.orders_hub.auth.response;

public record GoogleUserResponse(
        String subject,
        String email,
        String name,
        String pictureUrl
) {
}
