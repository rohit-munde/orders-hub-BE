package com.indiedev.orders_hub.gmail.dto;

import java.util.List;

public record GmailSyncPreview(
        String query,
        int messageCount,
        boolean nextPageTokenAvailable,
        List<GmailMessageSummary> messages
) {
    public GmailSyncPreview {
        messages = List.copyOf(messages);
    }
}
