package com.indiedev.orders_hub.user.response;

/**
 * {
 *   "success": true,
 *   "message": "User summary fetched successfully",
 *   "payload": {
 *     "name": "Rohit Munde",
 *     "pictureUrl": "https://...",
 *     "connectedInboxCount": 2,
 *     "trackedOrderCount": 230
 *   }
 * }
 *
 * */
public record UserDetailsResponse(
        String name,
        String pictureUrl,
        int connectedInboxCount,
        long trackedOrderCount
) {
}
