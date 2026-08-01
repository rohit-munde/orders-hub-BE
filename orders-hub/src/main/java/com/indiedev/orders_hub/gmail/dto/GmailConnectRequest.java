package com.indiedev.orders_hub.gmail.dto;

import jakarta.validation.constraints.NotBlank;

public record GmailConnectRequest(
        @NotBlank(message = "Google server authorization code is required")
        String serverAuthCode
) {
}
