package com.indiedev.orders_hub.auth.service;

import jakarta.validation.constraints.NotBlank;

public record GoogleLoginRequest(
        @NotBlank(message = "Google ID token is required") String idToken
) {
}
