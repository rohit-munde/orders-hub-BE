package com.indiedev.orders_hub.gmail.dto;

import java.math.BigDecimal;
import java.util.List;

public record GmailOrderPreview(
        String gmailMessageId,
        String brandName,
        String orderNo,
        BigDecimal billAmount,
        Boolean paid,
        String otp,
        String status,
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
