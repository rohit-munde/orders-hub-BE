package com.indiedev.orders_hub.order.service;

import com.indiedev.orders_hub.connectedaccount.ConnectedAccountProvider;
import com.indiedev.orders_hub.connectedaccount.ConnectedAccountRepository;
import com.indiedev.orders_hub.order.Order;
import com.indiedev.orders_hub.order.OrderItem;
import com.indiedev.orders_hub.order.OrderRepository;
import com.indiedev.orders_hub.order.dto.OrderListResponse;
import com.indiedev.orders_hub.response.PageResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;

@Service
@RequiredArgsConstructor
public class OrderQueryService {

    private final OrderRepository orderRepository;
    private final ConnectedAccountRepository accountRepository;

    @Transactional(readOnly = true)
    public OrderListResponse getOrders(long userId, Pageable pageable) {
        Pageable safePageable = PageRequest.of(pageable.getPageNumber(), pageable.getPageSize());
        Instant lastSyncedAt = accountRepository
                .findFirstByUserIdAndProviderOrderByIdDesc(userId, ConnectedAccountProvider.GOOGLE)
                .map(account -> account.getLastSyncAt())
                .orElse(null);
        return new OrderListResponse(
                lastSyncedAt,
                PageResponse.from(orderRepository.findPageForUser(userId, safePageable)
                        .map(this::toResponse))
        );
    }

    private OrderListResponse.OrderResponse toResponse(Order order) {
        return new OrderListResponse.OrderResponse(
                order.getId(),
                order.getMerchantKey(),
                order.getBrandName(),
                order.getOrderNo(),
                order.getBillAmount(),
                order.getCurrency(),
                order.getPaid(),
                order.getStatus(),
                order.getPlacedAt(),
                order.getOrderItems().stream().map(this::toResponse).toList()
        );
    }

    private OrderListResponse.OrderItemResponse toResponse(OrderItem item) {
        return new OrderListResponse.OrderItemResponse(
                item.getId(),
                item.getProductName(),
                item.getProductUrl(),
                item.getQuantity(),
                item.getPrice()
        );
    }
}
