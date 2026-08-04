package com.indiedev.orders_hub.order.dto;

import java.time.Instant;

public record OrderSyncResponse(
        Outcome outcome,
        Instant lastSyncedAt,
        int candidateCount,
        int savedCount,
        int skippedCount,
        int ignoredCount,
        int failedCount
) {
    public static OrderSyncResponse cooldown(Instant lastSyncedAt) {
        return new OrderSyncResponse(Outcome.COOLDOWN, lastSyncedAt, 0, 0, 0, 0, 0);
    }

    public enum Outcome {
        COMPLETED,
        COOLDOWN
    }
}
