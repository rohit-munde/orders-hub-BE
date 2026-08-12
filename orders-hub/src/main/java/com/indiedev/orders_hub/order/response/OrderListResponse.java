package com.indiedev.orders_hub.order.response;

import com.indiedev.orders_hub.order.entity.OrderStatus;
import com.indiedev.orders_hub.common.response.PageResponse;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;

public record OrderListResponse(
        Instant lastSyncedAt,
        PageResponse<OrderResponse> orders
) {
    public record OrderResponse(
            long id,
            String merchantKey,
            String brandName,
            String orderNo,
            BigDecimal billAmount,
            BigDecimal refundAmount,
            String currency,
            Boolean paid,
            OrderStatus status,
            Instant placedAt,
            List<OrderItemResponse> items
    ) {
        public OrderResponse {
            items = List.copyOf(items);
        }
    }

    public record OrderItemResponse(
            long id,
            String productName,
            String productUrl,
            int quantity,
            BigDecimal price
    ) {
    }
}
