package com.indiedev.orders_hub.connectedaccount.request;

import jakarta.validation.constraints.NotBlank;

public record GoogleConnectedAccountRequest(
        @NotBlank(message = "Google server auth code is required")
        String serverAuthCode
) {
}
