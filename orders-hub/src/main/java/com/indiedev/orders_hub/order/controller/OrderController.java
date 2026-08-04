package com.indiedev.orders_hub.order.controller;

import com.indiedev.orders_hub.order.dto.OrderListResponse;
import com.indiedev.orders_hub.order.dto.OrderSyncResponse;
import com.indiedev.orders_hub.order.service.OrderQueryService;
import com.indiedev.orders_hub.order.service.OrderSyncService;
import com.indiedev.orders_hub.response.ApiSuccessResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PageableDefault;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/orders")
@RequiredArgsConstructor
public class OrderController {

    private final OrderQueryService queryService;
    private final OrderSyncService syncService;

    @GetMapping
    public ApiSuccessResponse<OrderListResponse> getOrders(
            @AuthenticationPrincipal Jwt jwt,
            @PageableDefault(size = 20) Pageable pageable
    ) {
        return new ApiSuccessResponse<>(
                "Orders fetched successfully",
                queryService.getOrders(userId(jwt), pageable)
        );
    }

    @PostMapping("/sync")
    public OrderSyncResponse sync(
            @AuthenticationPrincipal Jwt jwt,
            @RequestParam(defaultValue = "false") boolean force
    ) {
        return syncService.sync(userId(jwt), force);
    }

    private long userId(Jwt jwt) {
        Object claim = jwt.getClaim("userId");
        if (claim instanceof Number userId) {
            return userId.longValue();
        }
        throw new IllegalArgumentException("Authenticated user ID is missing");
    }
}
