package com.indiedev.orders_hub.gmail.dto;

import java.util.List;

public record GmailConnectResponse(
        long connectedAccountId,
        String email,
        int messageCount,
        List<GmailMessageSummary> messages
) {
}
