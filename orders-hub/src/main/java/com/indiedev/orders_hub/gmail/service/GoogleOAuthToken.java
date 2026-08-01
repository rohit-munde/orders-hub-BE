package com.indiedev.orders_hub.gmail.service;

public record GoogleOAuthToken(
        String accessToken,
        String refreshToken,
        long expiresIn,
        String scope,
        String tokenType
) {
}
