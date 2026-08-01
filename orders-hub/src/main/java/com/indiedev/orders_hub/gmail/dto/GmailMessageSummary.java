package com.indiedev.orders_hub.gmail.dto;

public record GmailMessageSummary(
        String gmailMessageId,
        String threadId,
        String subject,
        String from,
        String date
) {
}
