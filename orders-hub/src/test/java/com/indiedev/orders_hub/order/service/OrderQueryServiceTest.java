package com.indiedev.orders_hub.order.service;

import com.indiedev.orders_hub.connectedaccount.ConnectedAccount;
import com.indiedev.orders_hub.connectedaccount.ConnectedAccountProvider;
import com.indiedev.orders_hub.connectedaccount.ConnectedAccountRepository;
import com.indiedev.orders_hub.order.Order;
import com.indiedev.orders_hub.order.OrderItem;
import com.indiedev.orders_hub.order.OrderRepository;
import com.indiedev.orders_hub.order.OrderStatus;
import com.indiedev.orders_hub.order.dto.OrderListResponse;
import org.mockito.ArgumentCaptor;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.Arrays;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.*;

class OrderQueryServiceTest {

    private OrderRepository orderRepository;
    private ConnectedAccountRepository accountRepository;
    private OrderQueryService service;

    @BeforeEach
    void setUp() {
        orderRepository = mock(OrderRepository.class);
        accountRepository = mock(ConnectedAccountRepository.class);
        service = new OrderQueryService(orderRepository, accountRepository);
    }

    @Test
    void mapsApplicationOrderFieldsAndLastSyncTime() {
        Instant placedAt = Instant.parse("2026-08-03T10:00:00Z");
        Instant lastSyncedAt = Instant.parse("2026-08-03T12:00:00Z");
        Order order = order(placedAt);
        ConnectedAccount account = new ConnectedAccount();
        account.setLastSyncAt(lastSyncedAt);
        when(orderRepository.findPageForUser(eq(7L), any(Pageable.class)))
                .thenReturn(new PageImpl<>(List.of(order), PageRequest.of(1, 20), 21));
        when(accountRepository.findFirstByUserIdAndProviderOrderByIdDesc(
                7, ConnectedAccountProvider.GOOGLE
        )).thenReturn(Optional.of(account));

        OrderListResponse response = service.getOrders(
                7,
                PageRequest.of(1, 20, Sort.by("brandName").ascending())
        );

        assertEquals(lastSyncedAt, response.lastSyncedAt());
        assertEquals(1, response.orders().content().size());
        assertEquals(1, response.orders().pagination().page());
        assertEquals(20, response.orders().pagination().size());
        assertEquals(21, response.orders().pagination().totalElements());
        assertEquals(2, response.orders().pagination().totalPages());
        assertFalse(response.orders().pagination().hasNext());
        assertTrue(response.orders().pagination().hasPrevious());
        OrderListResponse.OrderResponse mapped = response.orders().content().getFirst();
        assertEquals(21, mapped.id());
        assertEquals("amazon.in", mapped.merchantKey());
        assertEquals("Amazon", mapped.brandName());
        assertEquals("ORDER-123", mapped.orderNo());
        assertEquals(new BigDecimal("1499.00"), mapped.billAmount());
        assertEquals("INR", mapped.currency());
        assertEquals(Boolean.TRUE, mapped.paid());
        assertEquals(OrderStatus.SHIPPED, mapped.status());
        assertEquals(placedAt, mapped.placedAt());
        assertEquals("USB-C Cable", mapped.items().getFirst().productName());

        ArgumentCaptor<Pageable> pageableCaptor = ArgumentCaptor.forClass(Pageable.class);
        verify(orderRepository).findPageForUser(eq(7L), pageableCaptor.capture());
        assertEquals(1, pageableCaptor.getValue().getPageNumber());
        assertEquals(20, pageableCaptor.getValue().getPageSize());
        assertTrue(pageableCaptor.getValue().getSort().isUnsorted());
    }

    @Test
    void returnsOrdersWithNoLastSyncTimeWhenGmailIsNotConnected() {
        when(orderRepository.findPageForUser(eq(7L), any(Pageable.class)))
                .thenReturn(new PageImpl<>(List.of(), PageRequest.of(0, 20), 0));
        when(accountRepository.findFirstByUserIdAndProviderOrderByIdDesc(
                7, ConnectedAccountProvider.GOOGLE
        )).thenReturn(Optional.empty());

        OrderListResponse response = service.getOrders(7, PageRequest.of(0, 20));

        assertNull(response.lastSyncedAt());
        assertEquals(List.of(), response.orders().content());
        assertEquals(0, response.orders().pagination().totalElements());
    }

    @Test
    void publicOrderDtosContainNoEmailOrCredentialFields() {
        Set<String> forbidden = Set.of(
                "otp", "gmailMessageId", "body", "accessToken", "refreshToken"
        );

        assertTrue(recordFields(OrderListResponse.class).stream().noneMatch(forbidden::contains));
        assertTrue(recordFields(OrderListResponse.OrderResponse.class).stream().noneMatch(forbidden::contains));
        assertTrue(recordFields(OrderListResponse.OrderItemResponse.class).stream().noneMatch(forbidden::contains));
    }

    private Set<String> recordFields(Class<?> recordType) {
        return Arrays.stream(recordType.getRecordComponents())
                .map(component -> component.getName())
                .collect(Collectors.toSet());
    }

    private Order order(Instant placedAt) {
        Order order = new Order();
        order.setId(21);
        order.setMerchantKey("amazon.in");
        order.setBrandName("Amazon");
        order.setOrderNo("ORDER-123");
        order.setBillAmount(new BigDecimal("1499.00"));
        order.setCurrency("INR");
        order.setPaid(true);
        order.setStatus(OrderStatus.SHIPPED);
        order.setPlacedAt(placedAt);
        OrderItem item = new OrderItem();
        item.setId(31);
        item.setProductName("USB-C Cable");
        item.setProductUrl("https://example.com/cable");
        item.setQuantity(2);
        item.setPrice(new BigDecimal("499.00"));
        order.getOrderItems().add(item);
        return order;
    }
}
