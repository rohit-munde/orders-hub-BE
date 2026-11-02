package com.indiedev.orders_hub.user.service;

import com.indiedev.orders_hub.connectedaccount.entity.ConnectedAccount;
import com.indiedev.orders_hub.connectedaccount.entity.ConnectedAccountProvider;
import com.indiedev.orders_hub.connectedaccount.entity.ConnectedAccountSyncStatus;
import com.indiedev.orders_hub.connectedaccount.repository.ConnectedAccountRepository;
import com.indiedev.orders_hub.exception.AuthenticatedUserMissingException;
import com.indiedev.orders_hub.gmail.service.GoogleAccessRevocationService;
import com.indiedev.orders_hub.order.entity.Order;
import com.indiedev.orders_hub.order.entity.OrderItem;
import com.indiedev.orders_hub.order.entity.OrderStatus;
import com.indiedev.orders_hub.order.repository.OrderRepository;
import com.indiedev.orders_hub.order.source.OrderEmailProcessingStatus;
import com.indiedev.orders_hub.order.source.OrderEmailSource;
import com.indiedev.orders_hub.order.source.OrderEmailSourceRepository;
import com.indiedev.orders_hub.user.entity.User;
import com.indiedev.orders_hub.user.repository.UserRepository;
import com.indiedev.orders_hub.user.response.AccountDeletionResponse;
import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;

import java.time.Instant;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

@DataJpaTest
class AccountDeletionServiceDataJpaTest {

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

    private final GoogleAccessRevocationService revocationService = mock(GoogleAccessRevocationService.class);

    @Test
    void deletesTheUserAndAllTheirPersonalData() {
        User user = userRepository.saveAndFlush(user("shopper@example.com", "google-user-1"));
        ConnectedAccount primary = connectedAccountRepository.save(connectedAccount(user, "primary@gmail.com"));
        ConnectedAccount backup = connectedAccountRepository.saveAndFlush(connectedAccount(user, "backup@gmail.com"));
        Order first = order(user, "ORDER-1");
        first.getOrderItems().add(orderItem(first));
        orderRepository.saveAndFlush(first);
        Order second = orderRepository.saveAndFlush(order(user, "ORDER-2"));
        orderEmailSourceRepository.save(source(primary, first, "message-1"));
        orderEmailSourceRepository.saveAndFlush(source(backup, second, "message-2"));

        User otherUser = userRepository.saveAndFlush(user("other@example.com", "google-user-2"));
        ConnectedAccount otherAccount = connectedAccountRepository.saveAndFlush(
                connectedAccount(otherUser, "other@gmail.com")
        );
        Order otherOrder = orderRepository.saveAndFlush(order(otherUser, "ORDER-9"));
        orderEmailSourceRepository.saveAndFlush(source(otherAccount, otherOrder, "message-9"));
        entityManager.clear();

        AccountDeletionResponse response = service().deleteAccount(user.getId());
        entityManager.flush();
        entityManager.clear();

        assertTrue(response.accountDeleted());
        assertEquals(2, response.connectedAccountsDeleted());
        assertEquals(2, response.ordersDeleted());
        assertTrue(userRepository.findById(user.getId()).isEmpty());
        assertEquals(0, connectedAccountRepository.countByUserId(user.getId()));
        assertEquals(0, orderRepository.countByUserId(user.getId()));
        assertEquals(0, orderEmailSourceRepository.countByConnectedAccountUserId(user.getId()));
        verify(revocationService).revokeAfterCommit(org.mockito.ArgumentMatchers.argThat(accounts ->
                accounts.stream().map(ConnectedAccount::getEmail).toList()
                        .equals(List.of("primary@gmail.com", "backup@gmail.com"))
        ));

        assertTrue(userRepository.findById(otherUser.getId()).isPresent());
        assertEquals(1, connectedAccountRepository.countByUserId(otherUser.getId()));
        assertEquals(1, orderRepository.countByUserId(otherUser.getId()));
        assertEquals(1, orderEmailSourceRepository.countByConnectedAccountUserId(otherUser.getId()));
    }

    @Test
    void rejectsDeletionForAnUnknownUser() {
        assertThrows(AuthenticatedUserMissingException.class, () -> service().deleteAccount(999_999));
    }

    private AccountDeletionService service() {
        return new AccountDeletionService(
                userRepository,
                connectedAccountRepository,
                orderEmailSourceRepository,
                orderRepository,
                revocationService
        );
    }

    private User user(String email, String googleId) {
        User user = new User();
        user.setEmail(email);
        user.setName("Shopper");
        user.setGoogleId(googleId);
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
