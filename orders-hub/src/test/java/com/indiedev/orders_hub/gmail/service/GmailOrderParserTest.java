package com.indiedev.orders_hub.gmail.service;

import com.indiedev.orders_hub.gmail.dto.GmailMessageContent;
import com.indiedev.orders_hub.gmail.dto.GmailOrderPreview;
import com.indiedev.orders_hub.order.entity.OrderStatus;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;

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
        assertEquals("Amazon", preview.brandName());
        assertEquals("ORDER-123", preview.orderNo());
        assertEquals(new BigDecimal("1499.00"), preview.billAmount());
        assertEquals("INR", preview.currency());
        assertEquals(Boolean.TRUE, preview.paid());
        assertEquals("482731", preview.otp());
        assertEquals(OrderStatus.SHIPPED, preview.status());
        assertEquals(receivedAt, preview.placedAt());
        assertEquals(List.of(), preview.orderItems());
        assertEquals(10, parser.version());
    }

    @Test
    void parsesBillAmountPositionedAboveOrderNumber() {
        GmailMessageContent message = new GmailMessageContent(
                "message-amazon-above",
                "Your Amazon.in order",
                "Amazon.in <shipment-tracking@amazon.in>",
                """
                        Order Total: ₹679.00
                        Your package has been delivered.
                        Order #407-5165755-2113155
                        """,
                Instant.parse("2026-09-01T17:06:00Z")
        );

        GmailOrderPreview preview = parser.parse(message);

        assertEquals("407-5165755-2113155", preview.orderNo());
        assertEquals(new BigDecimal("679.00"), preview.billAmount());
        assertEquals("INR", preview.currency());
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
    void treatsFutureDeliveryPromiseAsConfirmedInsteadOfDelivered() {
        GmailMessageContent message = new GmailMessageContent(
                "message-future-delivery",
                "Your Amazon.in order has been placed",
                "Amazon.in <auto-confirm@amazon.in>",
                """
                        Your order has been placed.
                        Order #407-3526300-5739560
                        Total: ₹701.55
                        Your package will be delivered by Aug 14.
                        """,
                Instant.parse("2026-08-10T14:25:00Z")
        );

        GmailOrderPreview preview = parser.parse(message);

        assertEquals("407-3526300-5739560", preview.orderNo());
        assertEquals(OrderStatus.CONFIRMED, preview.status());
    }

    @Test
    void ignoresPlainWordsAfterOrderIdMarkers() {
        GmailMessageContent message = new GmailMessageContent(
                "message-please",
                "Account notice",
                "Coinstuff <orders@coinsstuff.com>",
                """
                        Please order ID: Please keep this email for your records.
                        Total: ₹2,900.92
                        """,
                Instant.parse("2026-08-11T10:52:00Z")
        );

        GmailOrderPreview preview = parser.parse(message);

        assertNull(preview.orderNo());
    }

    @Test
    void ignoresInstructionTextBeforeActualOrderNumberInNoteEmails() {
        GmailMessageContent message = new GmailMessageContent(
                "message-coinsstuff-note",
                "Note added to your coinsstuff order",
                "Coinstuff <orders@coinsstuff.com>",
                """
                        The following note has been added to your order:
                        Please click on the below url to track your Shipments:
                        https://shiprocket.co/tracking/77910186900

                        As a reminder, here are your order details:

                        [Order #13456] (August 9, 2026)
                        Product Quantity Price
                        Chhatrapati Shivaji Maharaj Coins 1 ₹799.00
                        Subtotal: ₹2,796.00
                        """,
                Instant.parse("2026-08-11T10:52:00Z")
        );

        List<GmailOrderPreview> previews = parser.parseAll(message);

        assertEquals(1, previews.size());
        assertEquals("13456", previews.getFirst().orderNo());
        assertEquals(new BigDecimal("2796.00"), previews.getFirst().billAmount());
    }

    @Test
    void parsesRefundEmailToRefundedStatus() {
        GmailMessageContent message = new GmailMessageContent(
                "message-refund",
                "Your refund for essart Faux Leather Luxury....",
                "Amazon <return@amazon.in>",
                """
                        Your refund was issued.
                        The financial institution is processing your refund.
                        ₹1,999.00 will be credited to the original payment method by Aug 15.
                        Order #407-3385584-8184336
                        Total refund: ₹1,999.00
                        """,
                Instant.parse("2026-08-09T12:08:00Z")
        );

        GmailOrderPreview preview = parser.parse(message);

        assertEquals(OrderStatus.REFUNDED, preview.status());
        assertEquals("407-3385584-8184336", preview.orderNo());
        assertEquals(new BigDecimal("1999.00"), preview.billAmount());
        assertEquals("INR", preview.currency());
    }

    @Test
    void parsesEachOrderNumberInOneEmailAsASeparatePreview() {
        Instant receivedAt = Instant.parse("2026-08-10T14:15:00Z");
        GmailMessageContent message = new GmailMessageContent(
                "message-multi-order",
                "Your Amazon.in order confirmation",
                "Amazon.in <auto-confirm@amazon.in>",
                """
                        Your order has been placed.
                        Order #407-1111111-1111111
                        Total: ₹499.00

                        Your order has been placed.
                        Order #407-2222222-2222222
                        Total: ₹799.00
                        """,
                receivedAt
        );

        List<GmailOrderPreview> previews = parser.parseAll(message);

        assertEquals(2, previews.size());
        assertEquals("407-1111111-1111111", previews.get(0).orderNo());
        assertEquals(new BigDecimal("499.00"), previews.get(0).billAmount());
        assertEquals("407-2222222-2222222", previews.get(1).orderNo());
        assertEquals(new BigDecimal("799.00"), previews.get(1).billAmount());
        assertTrue(previews.stream().allMatch(preview -> "message-multi-order".equals(preview.gmailMessageId())));
        assertTrue(previews.stream().allMatch(preview -> receivedAt.equals(preview.placedAt())));
    }
}
