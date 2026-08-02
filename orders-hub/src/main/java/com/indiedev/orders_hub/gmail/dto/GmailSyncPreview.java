package com.indiedev.orders_hub.gmail.dto;

public record GmailSyncPreview(
        String query,
        String gmailMessageId,
        GmailOrderPreview orderPreview
) {
}
