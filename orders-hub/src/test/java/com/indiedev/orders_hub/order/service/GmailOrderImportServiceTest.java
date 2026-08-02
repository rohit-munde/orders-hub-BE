package com.indiedev.orders_hub.order.service;

import com.indiedev.orders_hub.connectedaccount.ConnectedAccount;
import com.indiedev.orders_hub.gmail.dto.GmailOrderPreview;
import com.indiedev.orders_hub.order.Order;
import com.indiedev.orders_hub.order.OrderRepository;
import com.indiedev.orders_hub.order.OrderStatus;
import com.indiedev.orders_hub.order.source.OrderEmailProcessingStatus;
import com.indiedev.orders_hub.order.source.OrderEmailSource;
import com.indiedev.orders_hub.order.source.OrderEmailSourceRepository;
import com.indiedev.orders_hub.user.User;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;

import static com.indiedev.orders_hub.order.service.GmailOrderImportService.Outcome.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

class GmailOrderImportServiceTest {

    private OrderRepository orderRepository;
    private OrderEmailSourceRepository sourceRepository;
    private GmailOrderImportService service;
    private ConnectedAccount account;

    @BeforeEach
    void setUp() {
        orderRepository = mock(OrderRepository.class);
        sourceRepository = mock(OrderEmailSourceRepository.class);
        service = new GmailOrderImportService(orderRepository, sourceRepository);

        User user = new User();
        user.setId(7);
        user.setEmail("shopper@example.com");
        account = new ConnectedAccount();
        account.setId(11);
        account.setUser(user);
    }

    @Test
    void createsOrderAndLinksImportedSourceWithoutPersistingOtp() {
        GmailOrderPreview candidate = candidate(
                "message-1", "amazon.in", "Amazon", " order-123 ",
                new BigDecimal("1499.00"), "INR", true, "482731", OrderStatus.SHIPPED
        );
        when(sourceRepository.findByConnectedAccountIdAndGmailMessageId(11, "message-1"))
                .thenReturn(Optional.empty());
        when(orderRepository.findByUserIdAndMerchantKeyAndOrderNo(7, "amazon.in", "ORDER-123"))
                .thenReturn(Optional.empty());
        when(orderRepository.save(any(Order.class))).thenAnswer(invocation -> invocation.getArgument(0));

        GmailOrderImportService.ImportResult result = service.importOrder(account, candidate, 1);

        assertEquals(SAVED, result.outcome());
        assertEquals("amazon.in", result.order().getMerchantKey());
        assertEquals("ORDER-123", result.order().getOrderNo());
        assertEquals(new BigDecimal("1499.00"), result.order().getBillAmount());
        assertEquals("INR", result.order().getCurrency());
        assertEquals(Boolean.TRUE, result.order().getPaid());
        assertEquals(OrderStatus.SHIPPED, result.order().getStatus());
        assertNull(result.order().getOtp());

        ArgumentCaptor<OrderEmailSource> source = ArgumentCaptor.forClass(OrderEmailSource.class);
        verify(sourceRepository).save(source.capture());
        assertSame(result.order(), source.getValue().getOrder());
        assertEquals(OrderEmailProcessingStatus.IMPORTED, source.getValue().getProcessingStatus());
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

        GmailOrderImportService.ImportResult result = service.importOrder(account, candidate, 1);

        assertSame(existing, result.order());
        assertEquals("Amazon Store", existing.getBrandName());
        assertEquals(new BigDecimal("1499.00"), existing.getBillAmount());
        assertEquals("INR", existing.getCurrency());
        assertEquals(Boolean.TRUE, existing.getPaid());
        assertEquals(OrderStatus.DELIVERED, existing.getStatus());
        assertNull(existing.getOtp());
    }

    @Test
    void recordsCandidateWithoutOrderIdentityAsIgnored() {
        GmailOrderPreview candidate = candidate(
                "message-3", "amazon.in", "Amazon", null,
                null, null, null, null, OrderStatus.UNKNOWN
        );
        when(sourceRepository.findByConnectedAccountIdAndGmailMessageId(11, "message-3"))
                .thenReturn(Optional.empty());

        GmailOrderImportService.ImportResult result = service.importOrder(account, candidate, 1);

        assertEquals(IGNORED, result.outcome());
        assertNull(result.order());
        verifyNoInteractions(orderRepository);
        ArgumentCaptor<OrderEmailSource> source = ArgumentCaptor.forClass(OrderEmailSource.class);
        verify(sourceRepository).save(source.capture());
        assertEquals(OrderEmailProcessingStatus.IGNORED, source.getValue().getProcessingStatus());
        assertEquals("Missing merchant or order number", source.getValue().getFailureReason());
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

        assertFalse(service.shouldProcess(11, "imported", 2));
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
        return new GmailOrderPreview(
                messageId, merchantKey, brandName, orderNo, amount, currency,
                paid, otp, status, List.of()
        );
    }

    private OrderEmailSource source(OrderEmailProcessingStatus status, int parserVersion) {
        OrderEmailSource source = new OrderEmailSource();
        source.setProcessingStatus(status);
        source.setParserVersion(parserVersion);
        return source;
    }
}
