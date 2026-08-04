package com.indiedev.orders_hub.order.controller;

import com.indiedev.orders_hub.GlobalExceptionHandling;
import com.indiedev.orders_hub.config.SecurityConfig;
import com.indiedev.orders_hub.order.OrderStatus;
import com.indiedev.orders_hub.order.dto.OrderListResponse;
import com.indiedev.orders_hub.order.dto.OrderSyncResponse;
import com.indiedev.orders_hub.order.service.OrderQueryService;
import com.indiedev.orders_hub.order.service.OrderSyncService;
import com.indiedev.orders_hub.response.PageMetadata;
import com.indiedev.orders_hub.response.PageResponse;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.data.domain.Pageable;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(
        controllers = OrderController.class,
        properties = "app.jwt.secret=MDEyMzQ1Njc4OWFiY2RlZjAxMjM0NTY3ODlhYmNkZWY="
)
@Import({SecurityConfig.class, GlobalExceptionHandling.class})
class OrderControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private OrderQueryService queryService;

    @MockitoBean
    private OrderSyncService syncService;

    @Test
    void rejectsAnonymousOrderRequests() throws Exception {
        mockMvc.perform(get("/api/v1/orders"))
                .andExpect(status().isUnauthorized());
        mockMvc.perform(post("/api/v1/orders/sync"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void listsOnlyTheAuthenticatedUsersOrders() throws Exception {
        Instant placedAt = Instant.parse("2026-08-03T10:00:00Z");
        Instant lastSyncedAt = Instant.parse("2026-08-03T12:00:00Z");
        when(queryService.getOrders(eq(7L), any(Pageable.class))).thenReturn(new OrderListResponse(
                lastSyncedAt,
                new PageResponse<>(
                        List.of(new OrderListResponse.OrderResponse(
                                21,
                                "amazon.in",
                                "Amazon",
                                "ORDER-123",
                                new BigDecimal("1499.00"),
                                "INR",
                                true,
                                OrderStatus.SHIPPED,
                                placedAt,
                                List.of()
                        )),
                        new PageMetadata(0, 20, 1, 1, false, false)
                )
        ));

        mockMvc.perform(get("/api/v1/orders").with(jwt().jwt(token -> token.claim("userId", 7L))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.message").value("Orders fetched successfully"))
                .andExpect(jsonPath("$.payload.lastSyncedAt").value("2026-08-03T12:00:00Z"))
                .andExpect(jsonPath("$.payload.orders.content[0].id").value(21))
                .andExpect(jsonPath("$.payload.orders.content[0].orderNo").value("ORDER-123"))
                .andExpect(jsonPath("$.payload.orders.content[0].placedAt").value("2026-08-03T10:00:00Z"))
                .andExpect(jsonPath("$.payload.orders.content[0].otp").doesNotExist())
                .andExpect(jsonPath("$.payload.orders.content[0].gmailMessageId").doesNotExist())
                .andExpect(jsonPath("$.payload.orders.pagination.page").value(0))
                .andExpect(jsonPath("$.payload.orders.pagination.size").value(20))
                .andExpect(jsonPath("$.payload.orders.pagination.totalElements").value(1))
                .andExpect(jsonPath("$.payload.orders.pagination.totalPages").value(1))
                .andExpect(jsonPath("$.payload.orders.pagination.hasNext").value(false))
                .andExpect(jsonPath("$.payload.orders.pagination.hasPrevious").value(false));

        ArgumentCaptor<Pageable> pageableCaptor = ArgumentCaptor.forClass(Pageable.class);
        verify(queryService).getOrders(eq(7L), pageableCaptor.capture());
        assertEquals(0, pageableCaptor.getValue().getPageNumber());
        assertEquals(20, pageableCaptor.getValue().getPageSize());
    }

    @Test
    void forwardsRequestedPageAndSize() throws Exception {
        when(queryService.getOrders(eq(7L), any(Pageable.class))).thenReturn(new OrderListResponse(
                null,
                new PageResponse<>(List.of(), new PageMetadata(2, 10, 0, 0, false, true))
        ));

        mockMvc.perform(get("/api/v1/orders?page=2&size=10&sort=brandName,asc")
                        .with(jwt().jwt(token -> token.claim("userId", 7L))))
                .andExpect(status().isOk());

        ArgumentCaptor<Pageable> pageableCaptor = ArgumentCaptor.forClass(Pageable.class);
        verify(queryService).getOrders(eq(7L), pageableCaptor.capture());
        assertEquals(2, pageableCaptor.getValue().getPageNumber());
        assertEquals(10, pageableCaptor.getValue().getPageSize());
    }

    @Test
    void syncDefaultsToCooldownAwareMode() throws Exception {
        when(syncService.sync(7, false)).thenReturn(OrderSyncResponse.cooldown(
                Instant.parse("2026-08-03T12:00:00Z")
        ));

        mockMvc.perform(post("/api/v1/orders/sync")
                        .with(jwt().jwt(token -> token.claim("userId", 7L))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.outcome").value("COOLDOWN"));

        verify(syncService).sync(7, false);
    }

    @Test
    void forcedSyncIsForwardedForPullToRefresh() throws Exception {
        when(syncService.sync(7, true)).thenReturn(new OrderSyncResponse(
                OrderSyncResponse.Outcome.COMPLETED,
                Instant.parse("2026-08-03T12:00:00Z"),
                4, 2, 1, 1, 0
        ));

        mockMvc.perform(post("/api/v1/orders/sync?force=true")
                        .with(jwt().jwt(token -> token.claim("userId", 7L))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.outcome").value("COMPLETED"))
                .andExpect(jsonPath("$.savedCount").value(2));

        verify(syncService).sync(7, true);
    }

    @Test
    void rejectsAnApplicationTokenWithoutAUserIdClaim() throws Exception {
        mockMvc.perform(get("/api/v1/orders").with(jwt()))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("Authenticated user ID is missing"));
    }
}
