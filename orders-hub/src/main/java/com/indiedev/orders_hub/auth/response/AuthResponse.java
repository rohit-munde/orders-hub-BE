package com.indiedev.orders_hub.auth.response;

import com.indiedev.orders_hub.gmail.dto.GmailSyncPreview;

public record AuthResponse(
        String appToken,
        UserInfo user,
        ConnectedAccountInfo connectedAccount,
        GmailSyncPreview syncPreview
) {

    public record UserInfo(long id, String name, String email, String pictureUrl) {
    }

    public record ConnectedAccountInfo(long id, String email, String status) {
    }
}
