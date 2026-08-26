package com.indiedev.orders_hub.connectedaccount.service;

import com.indiedev.orders_hub.connectedaccount.entity.ConnectedAccount;
import com.indiedev.orders_hub.connectedaccount.entity.ConnectedAccountProvider;
import com.indiedev.orders_hub.connectedaccount.entity.ConnectedAccountSyncStatus;
import com.indiedev.orders_hub.connectedaccount.repository.ConnectedAccountRepository;
import com.indiedev.orders_hub.gmail.service.GmailConnectionService;
import com.indiedev.orders_hub.gmail.service.GoogleOAuthService;
import com.indiedev.orders_hub.order.entity.Order;
import com.indiedev.orders_hub.order.entity.OrderItem;
import com.indiedev.orders_hub.order.entity.OrderStatus;
import com.indiedev.orders_hub.order.repository.OrderRepository;
import com.indiedev.orders_hub.order.source.OrderEmailProcessingStatus;
import com.indiedev.orders_hub.order.source.OrderEmailSource;
import com.indiedev.orders_hub.order.source.OrderEmailSourceRepository;
import com.indiedev.orders_hub.user.entity.User;
import com.indiedev.orders_hub.user.repository.UserRepository;
import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;

import java.time.Instant;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.mock;

@DataJpaTest
class ConnectedAccountServiceDataJpaTest {

    @Autowired
    private EntityManager entityManager;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private ConnectedAccountRepository connectedAccountRepository;

    @Autowired
    private OrderEmailSourceRepository orderEmailSourceRepository;

    @Autowired
    private OrderRepository orderRepository;

    @Test
    void disconnectDeletesAllEmailSourcesBeforeDeletingImportedOrders() {
        User user = user();
        userRepository.saveAndFlush(user);
        ConnectedAccount disconnectingAccount = connectedAccount(user, "primary@gmail.com");
        ConnectedAccount remainingAccount = connectedAccount(user, "backup@gmail.com");
        connectedAccountRepository.save(disconnectingAccount);
        connectedAccountRepository.saveAndFlush(remainingAccount);
        Order order = order(user, "ORDER-123");
        OrderItem item = orderItem(order);
        order.getOrderItems().add(item);
        orderRepository.saveAndFlush(order);
        orderEmailSourceRepository.saveAndFlush(source(disconnectingAccount, order));
        Order remainingAccountOrder = order(user, "ORDER-456");
        orderRepository.saveAndFlush(remainingAccountOrder);
        orderEmailSourceRepository.saveAndFlush(source(remainingAccount, remainingAccountOrder, "message-2"));

        service().disconnectGoogle(user.getId(), disconnectingAccount.getId());
        entityManager.flush();

        assertEquals(0, orderRepository.countByUserId(user.getId()));
        assertEquals(0, orderEmailSourceRepository.countByConnectedAccountId(disconnectingAccount.getId()));
        assertEquals(0, orderEmailSourceRepository.countByConnectedAccountId(remainingAccount.getId()));
        assertEquals(1, connectedAccountRepository.countByUserIdAndProvider(
                user.getId(),
                ConnectedAccountProvider.GOOGLE
        ));
    }

    private ConnectedAccountService service() {
        return new ConnectedAccountService(
                userRepository,
                mock(GoogleOAuthService.class),
                mock(GmailConnectionService.class),
                connectedAccountRepository,
                orderEmailSourceRepository,
                orderRepository
        );
    }

    private User user() {
        User user = new User();
        user.setEmail("shopper@example.com");
        user.setName("Shopper");
        user.setGoogleId("google-user-1");
        return user;
    }

    private ConnectedAccount connectedAccount(User user, String email) {
        ConnectedAccount account = new ConnectedAccount();
        account.setProvider(ConnectedAccountProvider.GOOGLE);
        account.setEmail(email);
        account.setSyncStatus(ConnectedAccountSyncStatus.SYNCED);
        account.setUser(user);
        return account;
    }

    private Order order(User user, String orderNo) {
        Order order = new Order();
        order.setUser(user);
        order.setOrderNo(orderNo);
        order.setStatus(OrderStatus.CONFIRMED);
        return order;
    }

    private OrderItem orderItem(Order order) {
        OrderItem item = new OrderItem();
        item.setOrder(order);
        item.setProductName("USB Cable");
        item.setQuantity(1);
        return item;
    }

    private OrderEmailSource source(ConnectedAccount account, Order order) {
        return source(account, order, "message-1");
    }

    private OrderEmailSource source(ConnectedAccount account, Order order, String gmailMessageId) {
        OrderEmailSource source = new OrderEmailSource();
        source.setConnectedAccount(account);
        source.setGmailMessageId(gmailMessageId);
        source.setOrder(order);
        source.setProcessingStatus(OrderEmailProcessingStatus.IMPORTED);
        source.setParserVersion(1);
        source.setProcessedAt(Instant.now());
        return source;
    }
}
