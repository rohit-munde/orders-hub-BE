package com.indiedev.orders_hub.gmail.service;

import com.indiedev.orders_hub.gmail.dto.GmailMessageContent;
import com.indiedev.orders_hub.gmail.dto.GmailOrderPreview;
import com.indiedev.orders_hub.order.OrderStatus;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.Locale;

import static org.junit.jupiter.api.Assertions.*;

class GmailOrderParserTest {

    private final GmailOrderParser parser = new GmailOrderParser();

    @Test
    void extractsOrderFieldsFromDecodedEmailContent() {
        Instant receivedAt = Instant.parse("2026-08-02T12:30:00Z");
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
                        """,
                receivedAt
        );

        GmailOrderPreview preview = parser.parse(message);

        assertEquals("message-1", preview.gmailMessageId());
        assertEquals("amazon.in", preview.merchantKey());
        assertEquals("Amazon", preview.brandName());
        assertEquals("ORDER-123", preview.orderNo());
        assertEquals(new BigDecimal("1499.00"), preview.billAmount());
        assertEquals("INR", preview.currency());
        assertEquals(Boolean.TRUE, preview.paid());
        assertEquals("482731", preview.otp());
        assertEquals(OrderStatus.SHIPPED, preview.status());
        assertEquals(receivedAt, preview.placedAt());
        assertEquals(List.of(), preview.orderItems());
        assertEquals(2, parser.version());
    }

    @Test
    void returnsPartialPreviewWhenOptionalFieldsAreMissing() {
        GmailMessageContent message = new GmailMessageContent(
                "message-2",
                "Your receipt",
                "orders@shop.example",
                "Thanks for shopping with us.",
                null
        );

        GmailOrderPreview preview = parser.parse(message);

        assertEquals("shop.example", preview.brandName());
        assertEquals("shop.example", preview.merchantKey());
        assertNull(preview.orderNo());
        assertNull(preview.billAmount());
        assertNull(preview.currency());
        assertNull(preview.paid());
        assertNull(preview.otp());
        assertEquals(OrderStatus.UNKNOWN, preview.status());
        assertEquals(List.of(), preview.orderItems());
    }

    @Test
    void treatsExplicitNotPaidTextAsUnpaid() {
        GmailMessageContent message = new GmailMessageContent(
                "message-3",
                "Payment update",
                "Store <orders@store.example>",
                "Payment status: not paid",
                null
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
                "Shipped yesterday\nOut for delivery this morning\nDelivered today",
                null
        );

        GmailOrderPreview preview = parser.parse(message);

        assertEquals(OrderStatus.DELIVERED, preview.status());
    }

    @Test
    void leavesMerchantKeyNullWhenSenderHasNoEmailDomain() {
        GmailMessageContent message = new GmailMessageContent(
                "message-5", "Order confirmed", "Amazon", "Order ID: ORDER-500", null
        );

        GmailOrderPreview preview = parser.parse(message);

        assertNull(preview.merchantKey());
    }

    @Test
    void normalizesMerchantDomainIndependentOfSystemLocale() {
        Locale originalLocale = Locale.getDefault();
        try {
            Locale.setDefault(Locale.forLanguageTag("tr-TR"));
            GmailOrderPreview preview = parser.parse(new GmailMessageContent(
                    "message-locale",
                    "Order confirmed",
                    "Orders <orders@SHOPPING.IN>",
                    "Order ID: ABC-123",
                    null
            ));

            assertEquals("shopping.in", preview.merchantKey());
        } finally {
            Locale.setDefault(originalLocale);
        }
    }
}
