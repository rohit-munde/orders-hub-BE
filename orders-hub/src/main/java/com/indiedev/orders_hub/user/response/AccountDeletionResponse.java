package com.indiedev.orders_hub.user.response;

public record AccountDeletionResponse(
        boolean accountDeleted,
        long connectedAccountsDeleted,
        long ordersDeleted
) {
}
