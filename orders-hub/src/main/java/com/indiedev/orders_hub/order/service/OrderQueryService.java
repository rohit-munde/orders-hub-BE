package com.indiedev.orders_hub.order.service;

import com.indiedev.orders_hub.connectedaccount.entity.ConnectedAccountProvider;
import com.indiedev.orders_hub.connectedaccount.repository.ConnectedAccountRepository;
import com.indiedev.orders_hub.order.entity.Order;
import com.indiedev.orders_hub.order.entity.OrderItem;
import com.indiedev.orders_hub.order.repository.OrderRepository;
import com.indiedev.orders_hub.order.response.OrderListResponse;
import com.indiedev.orders_hub.common.response.PageResponse;
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
                order.getCompany() != null ? order.getCompany().getBrandName() : null,
                order.getOrderNo(),
                order.getCompany() != null ? order.getCompany().getLogoUrl() : null,
                order.getBillAmount() != null ? order.getBillAmount() : order.getRefundAmount(),
                order.getRefundAmount(),
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
