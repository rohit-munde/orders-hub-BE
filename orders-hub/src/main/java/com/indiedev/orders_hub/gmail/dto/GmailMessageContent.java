package com.indiedev.orders_hub.gmail.dto;

public record GmailMessageContent(
        String gmailMessageId,
        String subject,
        String from,
        String body
) {
}
