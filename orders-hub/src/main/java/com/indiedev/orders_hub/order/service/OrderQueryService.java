package com.indiedev.orders_hub.order.service;

import com.indiedev.orders_hub.connectedaccount.entity.ConnectedAccountProvider;
import com.indiedev.orders_hub.connectedaccount.repository.ConnectedAccountRepository;
import com.indiedev.orders_hub.order.entity.Order;
import com.indiedev.orders_hub.order.entity.OrderItem;
import com.indiedev.orders_hub.order.repository.OrderRepository;
import com.indiedev.orders_hub.order.response.OrderListResponse;
import com.indiedev.orders_hub.common.response.PageResponse;
import com.indiedev.orders_hub.security.token.TokenEncryptionService;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.time.Instant;

@Service
@RequiredArgsConstructor
public class OrderQueryService {

    private final OrderRepository orderRepository;
    private final ConnectedAccountRepository accountRepository;
    private final TokenEncryptionService encryptionService;

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
                deliveryOtp(order),
                order.getOrderItems().stream().map(this::toResponse).toList()
        );
    }

    /** Only orders that are still on their way expose their OTP. */
    private String deliveryOtp(Order order) {
        if (!StringUtils.hasText(order.getOtp()) || order.getStatus() == null || order.getStatus().isFinal()) {
            return null;
        }
        try {
            return encryptionService.decrypt(order.getOtp());
        } catch (RuntimeException exception) {
            return null;
        }
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
