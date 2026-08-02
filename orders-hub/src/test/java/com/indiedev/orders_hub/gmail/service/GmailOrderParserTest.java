package com.indiedev.orders_hub.gmail.service;

import com.indiedev.orders_hub.gmail.dto.GmailMessageContent;
import com.indiedev.orders_hub.gmail.dto.GmailOrderPreview;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class GmailOrderParserTest {

    private final GmailOrderParser parser = new GmailOrderParser();

    @Test
    void extractsOrderFieldsFromDecodedEmailContent() {
        GmailMessageContent message = new GmailMessageContent(
                "message-1",
                "Your order shipped",
                "Amazon <orders@amazon.in>",
                """
                        Order number: ORDER-123
                        Total amount: INR 1,499.00
                        Payment successful
                        Delivery OTP: 482731
                        Your order has shipped
                        """
        );

        GmailOrderPreview preview = parser.parse(message);

        assertEquals("message-1", preview.gmailMessageId());
        assertEquals("Amazon", preview.brandName());
        assertEquals("ORDER-123", preview.orderNo());
        assertEquals(new BigDecimal("1499.00"), preview.billAmount());
        assertEquals(Boolean.TRUE, preview.paid());
        assertEquals("482731", preview.otp());
        assertEquals("SHIPPED", preview.status());
        assertEquals(List.of(), preview.orderItems());
    }

    @Test
    void returnsPartialPreviewWhenOptionalFieldsAreMissing() {
        GmailMessageContent message = new GmailMessageContent(
                "message-2",
                "Your receipt",
                "orders@shop.example",
                "Thanks for shopping with us."
        );

        GmailOrderPreview preview = parser.parse(message);

        assertEquals("shop.example", preview.brandName());
        assertNull(preview.orderNo());
        assertNull(preview.billAmount());
        assertNull(preview.paid());
        assertNull(preview.otp());
        assertEquals("UNKNOWN", preview.status());
        assertEquals(List.of(), preview.orderItems());
    }

    @Test
    void treatsExplicitNotPaidTextAsUnpaid() {
        GmailMessageContent message = new GmailMessageContent(
                "message-3",
                "Payment update",
                "Store <orders@store.example>",
                "Payment status: not paid"
        );

        GmailOrderPreview preview = parser.parse(message);

        assertEquals(Boolean.FALSE, preview.paid());
    }

    @Test
    void usesDeliveredWhenEmailContainsEarlierDeliveryStates() {
        GmailMessageContent message = new GmailMessageContent(
                "message-4",
                "Your order was delivered",
                "Store <orders@store.example>",
                "Shipped yesterday\nOut for delivery this morning\nDelivered today"
        );

        GmailOrderPreview preview = parser.parse(message);

        assertEquals("DELIVERED", preview.status());
    }
}
