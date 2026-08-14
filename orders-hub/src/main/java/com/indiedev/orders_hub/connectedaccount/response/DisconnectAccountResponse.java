package com.indiedev.orders_hub.connectedaccount.response;

public record DisconnectAccountResponse(
        boolean connectedAccountDeleted,
        long ordersDeleted,
        long emailSourcesDeleted,
        String disconnectedEmailId
) {
}
