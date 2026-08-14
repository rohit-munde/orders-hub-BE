package com.indiedev.orders_hub.gmail.dto;

import java.time.Instant;

public record GmailMessageContent(
        String gmailMessageId,
        String subject,
        String from,
        String body,
        Instant receivedAt
) {
}
