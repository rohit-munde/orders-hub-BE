package com.indiedev.orders_hub.gmail.dto;

import java.util.List;

public record GmailSyncPreview(
        String query,
        int candidateCount,
        int savedCount,
        int skippedCount,
        int ignoredCount,
        int failedCount,
        List<GmailOrderPreview> orders
) {
    public GmailSyncPreview {
        orders = List.copyOf(orders);
    }
}
