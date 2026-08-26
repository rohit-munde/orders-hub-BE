package com.indiedev.orders_hub.gmail.dto;

import com.indiedev.orders_hub.order.entity.OrderStatus;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;

public record GmailOrderPreview(
        String gmailMessageId,
        String brandName,
        String orderNo,
        BigDecimal billAmount,
        String currency,
        Boolean paid,
        String otp,
        OrderStatus status,
        Instant placedAt,
        List<OrderItemPreview> orderItems
) {
    public GmailOrderPreview {
        orderItems = List.copyOf(orderItems);
    }

    public record OrderItemPreview(
            String productName,
            String productUrl,
            int quantity,
            BigDecimal price
    ) {
    }
}
