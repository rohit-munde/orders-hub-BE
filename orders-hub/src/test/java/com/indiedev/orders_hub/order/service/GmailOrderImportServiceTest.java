package com.indiedev.orders_hub.order.service;

import com.indiedev.orders_hub.connectedaccount.entity.ConnectedAccount;
import com.indiedev.orders_hub.gmail.dto.GmailOrderPreview;
import com.indiedev.orders_hub.order.entity.Order;
import com.indiedev.orders_hub.order.repository.OrderRepository;
import com.indiedev.orders_hub.order.entity.OrderStatus;
import com.indiedev.orders_hub.order.source.OrderEmailProcessingStatus;
import com.indiedev.orders_hub.order.source.OrderEmailSource;
import com.indiedev.orders_hub.order.source.OrderEmailSourceRepository;
import com.indiedev.orders_hub.user.entity.User;
import com.indiedev.orders_hub.user.repository.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.InOrder;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.Optional;

import static com.indiedev.orders_hub.order.service.GmailOrderImportService.Outcome.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

class GmailOrderImportServiceTest {

    private OrderRepository orderRepository;
    private OrderEmailSourceRepository sourceRepository;
    private UserRepository userRepository;
    private GmailOrderImportService service;
    private ConnectedAccount account;

    @BeforeEach
    void setUp() {
        orderRepository = mock(OrderRepository.class);
        sourceRepository = mock(OrderEmailSourceRepository.class);
        userRepository = mock(UserRepository.class);

        User user = new User();
        user.setId(7);
        user.setEmail("shopper@example.com");
        when(userRepository.findByIdForUpdate(7)).thenReturn(Optional.of(user));
        service = new GmailOrderImportService(orderRepository, sourceRepository, userRepository);
        account = new ConnectedAccount();
        account.setId(11);
        account.setUser(user);
    }

    @Test
    void createsOrderAndLinksImportedSourceWithoutPersistingOtp() {
        Instant placedAt = Instant.parse("2026-08-02T12:30:00Z");
        GmailOrderPreview candidate = candidate(
                "message-1", "amazon.in", "Amazon", " order-123 ",
                new BigDecimal("1499.00"), "INR", true, "482731", OrderStatus.SHIPPED, placedAt
        );
        when(sourceRepository.findByConnectedAccountIdAndGmailMessageId(11, "message-1"))
                .thenReturn(Optional.empty());
        when(orderRepository.findByUserIdAndMerchantKeyAndOrderNo(7, "amazon.in", "ORDER-123"))
                .thenReturn(Optional.empty());
        when(orderRepository.save(any(Order.class))).thenAnswer(invocation -> invocation.getArgument(0));

        GmailOrderImportService.ImportResult result = service.importOrder(account, "message-1", candidate, 1);

        assertEquals(SAVED, result.outcome());
        assertEquals("amazon.in", result.order().getMerchantKey());
        assertEquals("ORDER-123", result.order().getOrderNo());
        assertEquals(new BigDecimal("1499.00"), result.order().getBillAmount());
        assertEquals("INR", result.order().getCurrency());
        assertEquals(Boolean.TRUE, result.order().getPaid());
        assertEquals(OrderStatus.SHIPPED, result.order().getStatus());
        assertEquals(placedAt, result.order().getPlacedAt());
        assertNull(result.order().getOtp());

        ArgumentCaptor<OrderEmailSource> source = ArgumentCaptor.forClass(OrderEmailSource.class);
        verify(sourceRepository).save(source.capture());
        assertSame(result.order(), source.getValue().getOrder());
        assertEquals(OrderEmailProcessingStatus.IMPORTED, source.getValue().getProcessingStatus());
        InOrder lockThenRead = inOrder(userRepository, sourceRepository);
        lockThenRead.verify(userRepository).findByIdForUpdate(7);
        lockThenRead.verify(sourceRepository)
                .findByConnectedAccountIdAndGmailMessageId(11, "message-1");
    }

    @Test
    void importsDifferentOrderNumbersFromTheSameGmailMessage() {
        Order firstOrder = new Order();
        firstOrder.setUser(account.getUser());
        firstOrder.setMerchantKey("amazon.in");
        firstOrder.setOrderNo("407-1111111-1111111");
        firstOrder.setStatus(OrderStatus.CONFIRMED);
        OrderEmailSource existingMessageSource = source(OrderEmailProcessingStatus.IMPORTED, 6);
        existingMessageSource.setOrder(firstOrder);
        GmailOrderPreview first = candidate(
                "message-with-two-orders", "amazon.in", "Amazon", "407-1111111-1111111",
                new BigDecimal("499.00"), "INR", true, null, OrderStatus.CONFIRMED
        );
        GmailOrderPreview second = candidate(
                "message-with-two-orders", "amazon.in", "Amazon", "407-2222222-2222222",
                new BigDecimal("799.00"), "INR", true, null, OrderStatus.CONFIRMED
        );
        when(sourceRepository.findByConnectedAccountIdAndGmailMessageId(11, "message-with-two-orders"))
                .thenReturn(Optional.empty(), Optional.of(existingMessageSource));
        when(orderRepository.findByUserIdAndMerchantKeyAndOrderNo(
                7, "amazon.in", "407-1111111-1111111"
        )).thenReturn(Optional.empty());
        when(orderRepository.findByUserIdAndMerchantKeyAndOrderNo(
                7, "amazon.in", "407-2222222-2222222"
        )).thenReturn(Optional.empty());
        when(orderRepository.save(any(Order.class))).thenAnswer(invocation -> invocation.getArgument(0));

        GmailOrderImportService.ImportResult firstResult = service.importOrder(
                account, "message-with-two-orders", first, 6
        );
        GmailOrderImportService.ImportResult secondResult = service.importOrder(
                account, "message-with-two-orders", second, 6
        );

        assertEquals(SAVED, firstResult.outcome());
        assertEquals(SAVED, secondResult.outcome());
        assertNotSame(firstResult.order(), secondResult.order());
        assertEquals("407-1111111-1111111", firstResult.order().getOrderNo());
        assertEquals("407-2222222-2222222", secondResult.order().getOrderNo());
        ArgumentCaptor<OrderEmailSource> sources = ArgumentCaptor.forClass(OrderEmailSource.class);
        verify(sourceRepository, times(2)).save(sources.capture());
        assertSame(firstResult.order(), sources.getAllValues().get(0).getOrder());
        assertSame(secondResult.order(), sources.getAllValues().get(1).getOrder());
    }

    @Test
    void enrichesExistingOrderWithoutErasingKnownValuesOrRegressingStatus() {
        Order existing = new Order();
        existing.setUser(account.getUser());
        existing.setMerchantKey("amazon.in");
        existing.setOrderNo("ORDER-123");
        existing.setBrandName("Amazon Store");
        existing.setBillAmount(new BigDecimal("1499.00"));
        existing.setCurrency("INR");
        existing.setPaid(false);
        existing.setStatus(OrderStatus.DELIVERED);
        GmailOrderPreview candidate = candidate(
                "message-2", "amazon.in", null, "ORDER-123",
                null, null, true, "999999", OrderStatus.SHIPPED
        );
        when(sourceRepository.findByConnectedAccountIdAndGmailMessageId(11, "message-2"))
                .thenReturn(Optional.empty());
        when(orderRepository.findByUserIdAndMerchantKeyAndOrderNo(7, "amazon.in", "ORDER-123"))
                .thenReturn(Optional.of(existing));
        when(orderRepository.save(existing)).thenReturn(existing);

        GmailOrderImportService.ImportResult result = service.importOrder(account, "message-2", candidate, 1);

        assertSame(existing, result.order());
        assertEquals("Amazon Store", existing.getBrandName());
        assertEquals(new BigDecimal("1499.00"), existing.getBillAmount());
        assertEquals("INR", existing.getCurrency());
        assertEquals(Boolean.TRUE, existing.getPaid());
        assertEquals(OrderStatus.DELIVERED, existing.getStatus());
        assertNull(existing.getOtp());
    }

    @Test
    void updatesThePlacementTimeWhenALaterOrderEmailArrives() {
        Instant original = Instant.parse("2026-08-02T12:30:00Z");
        Order existing = existingOrder(original);
        Instant later = Instant.parse("2026-08-03T12:30:00Z");
        GmailOrderPreview laterEmail = candidate(
                "message-later", "amazon.in", "Amazon", "ORDER-123",
                null, null, null, null, OrderStatus.SHIPPED,
                later
        );
        when(sourceRepository.findByConnectedAccountIdAndGmailMessageId(11, "message-later"))
                .thenReturn(Optional.empty());
        when(orderRepository.findByUserIdAndMerchantKeyAndOrderNo(7, "amazon.in", "ORDER-123"))
                .thenReturn(Optional.of(existing));
        when(orderRepository.save(existing)).thenReturn(existing);

        service.importOrder(account, "message-later", laterEmail, 2);

        assertEquals(later, existing.getPlacedAt());
        assertEquals(OrderStatus.SHIPPED, existing.getStatus());
    }

    @Test
    void doesNotOverwriteWithAnOlderOrderEmail() {
        Instant original = Instant.parse("2026-08-02T12:30:00Z");
        Order existing = existingOrder(original);
        existing.setStatus(OrderStatus.SHIPPED);
        Instant earlier = Instant.parse("2026-08-01T12:30:00Z");
        GmailOrderPreview earlierEmail = candidate(
                "message-earlier", "amazon.in", "Amazon", "ORDER-123",
                null, null, null, null, OrderStatus.CONFIRMED, earlier
        );
        when(sourceRepository.findByConnectedAccountIdAndGmailMessageId(11, "message-earlier"))
                .thenReturn(Optional.empty());
        when(orderRepository.findByUserIdAndMerchantKeyAndOrderNo(7, "amazon.in", "ORDER-123"))
                .thenReturn(Optional.of(existing));
        when(orderRepository.save(existing)).thenReturn(existing);

        service.importOrder(account, "message-earlier", earlierEmail, 2);

        assertEquals(original, existing.getPlacedAt());
        assertEquals(OrderStatus.SHIPPED, existing.getStatus());
    }

    @Test
    void recordsCandidateWithoutOrderIdentityAsIgnored() {
        GmailOrderPreview candidate = candidate(
                "message-3", "amazon.in", "Amazon", null,
                null, null, null, null, OrderStatus.UNKNOWN
        );
        when(sourceRepository.findByConnectedAccountIdAndGmailMessageId(11, "message-3"))
                .thenReturn(Optional.empty());

        GmailOrderImportService.ImportResult result = service.importOrder(account, "message-3", candidate, 1);

        assertEquals(IGNORED, result.outcome());
        assertNull(result.order());
        verifyNoInteractions(orderRepository);
        ArgumentCaptor<OrderEmailSource> source = ArgumentCaptor.forClass(OrderEmailSource.class);
        verify(sourceRepository).save(source.capture());
        assertEquals(OrderEmailProcessingStatus.IGNORED, source.getValue().getProcessingStatus());
        assertEquals("Missing merchant or order number", source.getValue().getFailureReason());
    }

    @Test
    void removesStaleImportedOrderWhenReparseNoLongerFindsAnOrderIdentity() {
        Order staleOrder = new Order();
        staleOrder.setId(21);
        staleOrder.setUser(account.getUser());
        staleOrder.setMerchantKey("coinsstuff.com");
        staleOrder.setOrderNo("PLEASE");
        staleOrder.setStatus(OrderStatus.UNKNOWN);
        OrderEmailSource existingSource = source(OrderEmailProcessingStatus.IMPORTED, 6);
        existingSource.setId(31);
        existingSource.setOrder(staleOrder);
        GmailOrderPreview reparsedCandidate = candidate(
                "message-please", "coinsstuff.com", "coinsstuff.com", null,
                new BigDecimal("2900.92"), "INR", null, null, OrderStatus.UNKNOWN
        );
        when(sourceRepository.findByConnectedAccountIdAndGmailMessageId(11, "message-please"))
                .thenReturn(Optional.of(existingSource));
        when(sourceRepository.existsByOrderIdAndIdNot(21, 31)).thenReturn(false);

        GmailOrderImportService.ImportResult result = service.importOrder(
                account, "message-please", reparsedCandidate, 7
        );

        assertEquals(IGNORED, result.outcome());
        verify(orderRepository).delete(staleOrder);
        ArgumentCaptor<OrderEmailSource> source = ArgumentCaptor.forClass(OrderEmailSource.class);
        verify(sourceRepository).save(source.capture());
        assertNull(source.getValue().getOrder());
        assertEquals(OrderEmailProcessingStatus.IGNORED, source.getValue().getProcessingStatus());
    }

    @Test
    void keepsStaleOrderWhenAnotherEmailSourceStillReferencesIt() {
        Order staleOrder = new Order();
        staleOrder.setId(22);
        staleOrder.setUser(account.getUser());
        staleOrder.setMerchantKey("coinsstuff.com");
        staleOrder.setOrderNo("PLEASE");
        staleOrder.setStatus(OrderStatus.UNKNOWN);
        OrderEmailSource existingSource = source(OrderEmailProcessingStatus.IMPORTED, 6);
        existingSource.setId(32);
        existingSource.setOrder(staleOrder);
        GmailOrderPreview reparsedCandidate = candidate(
                "message-please", "coinsstuff.com", "coinsstuff.com", null,
                new BigDecimal("2900.92"), "INR", null, null, OrderStatus.UNKNOWN
        );
        when(sourceRepository.findByConnectedAccountIdAndGmailMessageId(11, "message-please"))
                .thenReturn(Optional.of(existingSource));
        when(sourceRepository.existsByOrderIdAndIdNot(22, 32)).thenReturn(true);

        service.importOrder(account, "message-please", reparsedCandidate, 7);

        verify(orderRepository, never()).delete(any(Order.class));
    }

    @Test
    void ignoresMismatchedParsedMessageIdUnderRequestedSourceIdentity() {
        GmailOrderPreview candidate = candidate(
                "different-message", "amazon.in", "Amazon", "ORDER-123",
                null, null, null, null, OrderStatus.CONFIRMED
        );
        when(sourceRepository.findByConnectedAccountIdAndGmailMessageId(11, "requested-message"))
                .thenReturn(Optional.empty());

        GmailOrderImportService.ImportResult result = service.importOrder(
                account, "requested-message", candidate, 1
        );

        assertEquals(IGNORED, result.outcome());
        verifyNoInteractions(orderRepository);
        ArgumentCaptor<OrderEmailSource> source = ArgumentCaptor.forClass(OrderEmailSource.class);
        verify(sourceRepository).save(source.capture());
        assertEquals("requested-message", source.getValue().getGmailMessageId());
        assertEquals(OrderEmailProcessingStatus.IGNORED, source.getValue().getProcessingStatus());
        assertEquals("Gmail message identity mismatch", source.getValue().getFailureReason());
    }

    @Test
    void addsParsedItemsOnlyWhenTheOrderHasNone() {
        Order existing = new Order();
        existing.setUser(account.getUser());
        existing.setMerchantKey("amazon.in");
        existing.setOrderNo("ORDER-123");
        existing.setStatus(OrderStatus.CONFIRMED);
        GmailOrderPreview candidate = new GmailOrderPreview(
                "message-items",
                "amazon.in",
                "Amazon",
                "ORDER-123",
                null,
                null,
                null,
                null,
                OrderStatus.CONFIRMED,
                null,
                List.of(new GmailOrderPreview.OrderItemPreview(
                        "USB-C Cable", "https://example.com/cable", 2, new BigDecimal("499.00")
                ))
        );
        when(sourceRepository.findByConnectedAccountIdAndGmailMessageId(11, "message-items"))
                .thenReturn(Optional.empty());
        when(orderRepository.findByUserIdAndMerchantKeyAndOrderNo(7, "amazon.in", "ORDER-123"))
                .thenReturn(Optional.of(existing));
        when(orderRepository.save(existing)).thenReturn(existing);

        service.importOrder(account, "message-items", candidate, 1);

        assertEquals(1, existing.getOrderItems().size());
        assertEquals("USB-C Cable", existing.getOrderItems().getFirst().getProductName());
        assertEquals(2, existing.getOrderItems().getFirst().getQuantity());
        assertSame(existing, existing.getOrderItems().getFirst().getOrder());
    }

    @Test
    void processesOnlyRetryableOrNewerParserSources() {
        OrderEmailSource imported = source(OrderEmailProcessingStatus.IMPORTED, 1);
        OrderEmailSource ignored = source(OrderEmailProcessingStatus.IGNORED, 1);
        OrderEmailSource failed = source(OrderEmailProcessingStatus.FAILED, 1);

        when(sourceRepository.findByConnectedAccountIdAndGmailMessageId(11, "imported"))
                .thenReturn(Optional.of(imported));
        when(sourceRepository.findByConnectedAccountIdAndGmailMessageId(11, "ignored"))
                .thenReturn(Optional.of(ignored));
        when(sourceRepository.findByConnectedAccountIdAndGmailMessageId(11, "failed"))
                .thenReturn(Optional.of(failed));

        assertTrue(service.shouldProcess(11, "imported", 2));
        assertFalse(service.shouldProcess(11, "imported", 1));
        assertFalse(service.shouldProcess(11, "ignored", 1));
        assertTrue(service.shouldProcess(11, "ignored", 2));
        assertTrue(service.shouldProcess(11, "failed", 1));
        assertTrue(service.shouldProcess(11, "new", 1));
    }

    @Test
    void recordsFetchOrParseFailureWithoutPrivateEmailContent() {
        when(sourceRepository.findByConnectedAccountIdAndGmailMessageId(11, "message-4"))
                .thenReturn(Optional.empty());

        service.recordFailure(account, "message-4", 1);

        ArgumentCaptor<OrderEmailSource> source = ArgumentCaptor.forClass(OrderEmailSource.class);
        verify(sourceRepository).save(source.capture());
        assertEquals(OrderEmailProcessingStatus.FAILED, source.getValue().getProcessingStatus());
        assertEquals("Unable to import Gmail message", source.getValue().getFailureReason());
        assertNull(source.getValue().getOrder());
    }

    @Test
    void doesNotDowngradeAnImportedSourceWhenRecordingFailure() {
        Order importedOrder = new Order();
        OrderEmailSource imported = source(OrderEmailProcessingStatus.IMPORTED, 1);
        imported.setOrder(importedOrder);
        when(sourceRepository.findByConnectedAccountIdAndGmailMessageId(11, "message-5"))
                .thenReturn(Optional.of(imported));

        service.recordFailure(account, "message-5", 1);

        assertEquals(OrderEmailProcessingStatus.IMPORTED, imported.getProcessingStatus());
        assertSame(importedOrder, imported.getOrder());
        verify(sourceRepository, never()).save(imported);
        InOrder lockThenRead = inOrder(userRepository, sourceRepository);
        lockThenRead.verify(userRepository).findByIdForUpdate(7);
        lockThenRead.verify(sourceRepository)
                .findByConnectedAccountIdAndGmailMessageId(11, "message-5");
    }

    @Test
    void doesNotDowngradeAnOlderImportedSourceWhenBackfillFails() {
        Order importedOrder = new Order();
        OrderEmailSource imported = source(OrderEmailProcessingStatus.IMPORTED, 1);
        imported.setOrder(importedOrder);
        when(sourceRepository.findByConnectedAccountIdAndGmailMessageId(11, "message-backfill"))
                .thenReturn(Optional.of(imported));

        service.recordFailure(account, "message-backfill", 2);

        assertEquals(OrderEmailProcessingStatus.IMPORTED, imported.getProcessingStatus());
        assertSame(importedOrder, imported.getOrder());
        verify(sourceRepository, never()).save(imported);
    }

    private GmailOrderPreview candidate(
            String messageId,
            String merchantKey,
            String brandName,
            String orderNo,
            BigDecimal amount,
            String currency,
            Boolean paid,
            String otp,
            OrderStatus status
    ) {
        return candidate(
                messageId, merchantKey, brandName, orderNo, amount, currency,
                paid, otp, status, null
        );
    }

    private GmailOrderPreview candidate(
            String messageId,
            String merchantKey,
            String brandName,
            String orderNo,
            BigDecimal amount,
            String currency,
            Boolean paid,
            String otp,
            OrderStatus status,
            Instant placedAt
    ) {
        return new GmailOrderPreview(
                messageId, merchantKey, brandName, orderNo, amount, currency,
                paid, otp, status, placedAt, List.of()
        );
    }

    @Test
    void importsRefundEmailAndUpdatesStatusAndTimestampToRefunded() {
        Order existing = new Order();
        existing.setUser(account.getUser());
        existing.setMerchantKey("amazon.in");
        existing.setOrderNo("407-3385584-8184336");
        existing.setPlacedAt(Instant.parse("2026-08-01T10:00:00Z"));
        existing.setStatus(OrderStatus.DELIVERED);
        existing.setBillAmount(new BigDecimal("2004.00"));

        Instant refundDate = Instant.parse("2026-08-09T12:08:00Z");
        GmailOrderPreview candidate = candidate(
                "msg-refund-1", "amazon.in", "Amazon", "407-3385584-8184336",
                new BigDecimal("1999.00"), "INR", true, null, OrderStatus.REFUNDED, refundDate
        );

        when(sourceRepository.findByConnectedAccountIdAndGmailMessageId(11, "msg-refund-1"))
                .thenReturn(Optional.empty());
        when(orderRepository.findByUserIdAndMerchantKeyAndOrderNo(7, "amazon.in", "407-3385584-8184336"))
                .thenReturn(Optional.of(existing));
        when(orderRepository.save(existing)).thenReturn(existing);

        GmailOrderImportService.ImportResult result = service.importOrder(account, "msg-refund-1", candidate, 3);

        assertEquals(SAVED, result.outcome());
        assertSame(existing, result.order());
        assertEquals(OrderStatus.REFUNDED, existing.getStatus());
        assertEquals(refundDate, existing.getPlacedAt());
        assertEquals(new BigDecimal("2004.00"), existing.getBillAmount());
        assertEquals(new BigDecimal("1999.00"), existing.getRefundAmount());
    }

    private Order existingOrder(Instant placedAt) {
        Order order = new Order();
        order.setUser(account.getUser());
        order.setMerchantKey("amazon.in");
        order.setOrderNo("ORDER-123");
        order.setStatus(OrderStatus.CONFIRMED);
        order.setPlacedAt(placedAt);
        return order;
    }

    private OrderEmailSource source(OrderEmailProcessingStatus status, int parserVersion) {
        OrderEmailSource source = new OrderEmailSource();
        source.setProcessingStatus(status);
        source.setParserVersion(parserVersion);
        return source;
    }
}
