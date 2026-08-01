package com.indiedev.orders_hub.auth.response;

public record AuthResponse(
        String accessToken,
        String tokenType,
        long expiresIn,
        GoogleUserResponse user
) {
}
