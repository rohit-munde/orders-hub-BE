package com.indiedev.orders_hub.order.source;

import com.indiedev.orders_hub.connectedaccount.ConnectedAccount;
import com.indiedev.orders_hub.connectedaccount.ConnectedAccountProvider;
import com.indiedev.orders_hub.connectedaccount.ConnectedAccountSyncStatus;
import com.indiedev.orders_hub.order.Order;
import com.indiedev.orders_hub.order.OrderRepository;
import com.indiedev.orders_hub.order.OrderStatus;
import com.indiedev.orders_hub.user.User;
import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.dao.DataIntegrityViolationException;

import java.time.Instant;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertThrows;

@DataJpaTest
class OrderEmailSourceRepositoryTest {

    @Autowired
    private EntityManager entityManager;

    @Autowired
    private OrderRepository orderRepository;

    @Autowired
    private OrderEmailSourceRepository sourceRepository;

    private User user;
    private ConnectedAccount account;

    @BeforeEach
    void setUp() {
        user = new User();
        user.setEmail("shopper@example.com");
        user.setName("Shopper");
        user.setGoogleId("google-user-1");
        entityManager.persist(user);

        account = new ConnectedAccount();
        account.setProvider(ConnectedAccountProvider.GOOGLE);
        account.setEmail("shopper@gmail.com");
        account.setSyncStatus(ConnectedAccountSyncStatus.SYNCED);
        account.setUser(user);
        entityManager.persist(account);
        entityManager.flush();
    }

    @Test
    void rejectsDuplicateGmailMessageForTheSameConnectedAccount() {
        sourceRepository.saveAndFlush(source("message-1"));

        assertThrows(
                DataIntegrityViolationException.class,
                () -> sourceRepository.saveAndFlush(source("message-1"))
        );
    }

    @Test
    void rejectsDuplicateOrderIdentityForTheSameUserAndMerchant() {
        orderRepository.saveAndFlush(order());

        assertThrows(
                DataIntegrityViolationException.class,
                () -> orderRepository.saveAndFlush(order())
        );
    }

    @Test
    void allowsLegacyOrderWithoutMerchantUntilItCanBeBackfilled() {
        Order legacyOrder = order();
        legacyOrder.setMerchantKey(null);

        assertDoesNotThrow(() -> orderRepository.saveAndFlush(legacyOrder));
    }

    private OrderEmailSource source(String gmailMessageId) {
        OrderEmailSource source = new OrderEmailSource();
        source.setConnectedAccount(account);
        source.setGmailMessageId(gmailMessageId);
        source.setProcessingStatus(OrderEmailProcessingStatus.IGNORED);
        source.setParserVersion(1);
        source.setProcessedAt(Instant.now());
        return source;
    }

    private Order order() {
        Order order = new Order();
        order.setUser(user);
        order.setMerchantKey("amazon.in");
        order.setOrderNo("ORDER-123");
        order.setStatus(OrderStatus.CONFIRMED);
        return order;
    }
}
