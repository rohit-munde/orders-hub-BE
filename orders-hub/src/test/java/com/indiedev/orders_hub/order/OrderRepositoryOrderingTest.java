package com.indiedev.orders_hub.order;

import com.indiedev.orders_hub.user.User;
import com.indiedev.orders_hub.user.UserRepository;
import org.hibernate.Hibernate;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

@DataJpaTest
class OrderRepositoryOrderingTest {

    @Autowired
    private OrderRepository orderRepository;

    @Autowired
    private UserRepository userRepository;

    @Test
    void returnsOnlyTheUsersOrdersNewestFirstWithNullPlacementLast() {
        User user = userRepository.saveAndFlush(user("shopper@example.com", "google-shopper"));
        User otherUser = userRepository.saveAndFlush(user("other@example.com", "google-other"));
        Instant tie = Instant.parse("2026-08-02T12:00:00Z");

        Order older = save(user, "ORDER-OLD", Instant.parse("2026-08-01T12:00:00Z"));
        Order firstTie = save(user, "ORDER-TIE-1", tie);
        Order secondTie = save(user, "ORDER-TIE-2", tie);
        Order newest = save(user, "ORDER-NEW", Instant.parse("2026-08-03T12:00:00Z"));
        Order unknownTime = save(user, "ORDER-UNKNOWN", null);
        save(otherUser, "ORDER-OTHER", Instant.parse("2026-08-04T12:00:00Z"));
        addItem(newest);

        List<Order> result = orderRepository.findAllForUserNewestFirst(user.getId());

        assertEquals(
                List.of(
                        newest.getId(),
                        secondTie.getId(),
                        firstTie.getId(),
                        older.getId(),
                        unknownTime.getId()
                ),
                result.stream().map(Order::getId).toList()
        );
        assertEquals(1, result.getFirst().getOrderItems().size());
        assertTrue(Hibernate.isInitialized(result.getFirst().getOrderItems()));
    }

    private User user(String email, String googleId) {
        User user = new User();
        user.setEmail(email);
        user.setName("Shopper");
        user.setGoogleId(googleId);
        return user;
    }

    private Order save(User user, String orderNo, Instant placedAt) {
        Order order = new Order();
        order.setUser(user);
        order.setMerchantKey("amazon.in");
        order.setOrderNo(orderNo);
        order.setStatus(OrderStatus.CONFIRMED);
        order.setPlacedAt(placedAt);
        return orderRepository.saveAndFlush(order);
    }

    private void addItem(Order order) {
        OrderItem item = new OrderItem();
        item.setOrder(order);
        item.setProductName("USB-C Cable");
        item.setQuantity(1);
        item.setPrice(new BigDecimal("499.00"));
        order.getOrderItems().add(item);
        orderRepository.saveAndFlush(order);
    }
}
