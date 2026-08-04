package com.indiedev.orders_hub.order;

import com.indiedev.orders_hub.user.User;
import com.indiedev.orders_hub.user.UserRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;

import java.time.Instant;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;

@DataJpaTest
class OrderRepositoryOrderingTest {

    @Autowired
    private OrderRepository orderRepository;

    @Autowired
    private UserRepository userRepository;

    @Test
    void pagesOnlyTheUsersOrdersNewestFirstWithNullPlacementLast() {
        User user = userRepository.saveAndFlush(user("shopper@example.com", "google-shopper"));
        User otherUser = userRepository.saveAndFlush(user("other@example.com", "google-other"));
        Instant tie = Instant.parse("2026-08-02T12:00:00Z");

        Order older = save(user, "ORDER-OLD", Instant.parse("2026-08-01T12:00:00Z"));
        Order firstTie = save(user, "ORDER-TIE-1", tie);
        Order secondTie = save(user, "ORDER-TIE-2", tie);
        Order newest = save(user, "ORDER-NEW", Instant.parse("2026-08-03T12:00:00Z"));
        Order unknownTime = save(user, "ORDER-UNKNOWN", null);
        save(otherUser, "ORDER-OTHER", Instant.parse("2026-08-04T12:00:00Z"));
        Page<Order> firstPage = orderRepository.findPageForUser(user.getId(), PageRequest.of(0, 3));
        Page<Order> secondPage = orderRepository.findPageForUser(user.getId(), PageRequest.of(1, 3));

        assertEquals(
                List.of(newest.getId(), secondTie.getId(), firstTie.getId()),
                firstPage.getContent().stream().map(Order::getId).toList()
        );
        assertEquals(List.of(older.getId(), unknownTime.getId()),
                secondPage.getContent().stream().map(Order::getId).toList());
        assertEquals(5, firstPage.getTotalElements());
        assertEquals(2, firstPage.getTotalPages());
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

}
