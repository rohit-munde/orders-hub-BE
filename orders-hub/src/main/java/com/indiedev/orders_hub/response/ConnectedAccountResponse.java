package com.indiedev.orders_hub.response;

import java.time.Instant;

public record ConnectedAccountResponse(
        long id,
        String provider,
        String email,
        String status,
        Instant lastSyncedAt
) {
}
