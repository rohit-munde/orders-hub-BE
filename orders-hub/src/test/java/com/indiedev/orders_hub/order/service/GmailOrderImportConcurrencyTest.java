package com.indiedev.orders_hub.order.service;

import com.indiedev.orders_hub.connectedaccount.ConnectedAccount;
import com.indiedev.orders_hub.connectedaccount.ConnectedAccountProvider;
import com.indiedev.orders_hub.connectedaccount.ConnectedAccountRepository;
import com.indiedev.orders_hub.connectedaccount.ConnectedAccountSyncStatus;
import com.indiedev.orders_hub.gmail.dto.GmailOrderPreview;
import com.indiedev.orders_hub.order.Order;
import com.indiedev.orders_hub.order.OrderRepository;
import com.indiedev.orders_hub.order.OrderStatus;
import com.indiedev.orders_hub.order.source.OrderEmailProcessingStatus;
import com.indiedev.orders_hub.order.source.OrderEmailSourceRepository;
import com.indiedev.orders_hub.user.User;
import com.indiedev.orders_hub.user.UserRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.context.annotation.Import;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

@DataJpaTest
@Import(GmailOrderImportService.class)
@Transactional(propagation = Propagation.NOT_SUPPORTED)
class GmailOrderImportConcurrencyTest {

    @Autowired
    private GmailOrderImportService importService;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private ConnectedAccountRepository accountRepository;

    @Autowired
    private OrderRepository orderRepository;

    @Autowired
    private OrderEmailSourceRepository sourceRepository;

    @Test
    void concurrentMessagesCreateOneOrderWithoutRegressingStatus() throws Exception {
        User user = new User();
        user.setEmail("concurrent@example.com");
        user.setName("Concurrent Shopper");
        user.setGoogleId("concurrent-google-user");
        user = userRepository.saveAndFlush(user);

        ConnectedAccount account = new ConnectedAccount();
        account.setProvider(ConnectedAccountProvider.GOOGLE);
        account.setEmail("concurrent@gmail.com");
        account.setSyncStatus(ConnectedAccountSyncStatus.SYNCED);
        account.setUser(user);
        account = accountRepository.saveAndFlush(account);

        ConnectedAccount savedAccount = account;
        CountDownLatch ready = new CountDownLatch(2);
        CountDownLatch start = new CountDownLatch(1);
        ExecutorService executor = Executors.newFixedThreadPool(2);
        try {
            Future<GmailOrderImportService.ImportResult> shipped = executor.submit(() -> {
                ready.countDown();
                start.await();
                return importService.importOrder(
                        savedAccount,
                        "message-shipped",
                        candidate("message-shipped", OrderStatus.SHIPPED),
                        1
                );
            });
            Future<GmailOrderImportService.ImportResult> delivered = executor.submit(() -> {
                ready.countDown();
                start.await();
                return importService.importOrder(
                        savedAccount,
                        "message-delivered",
                        candidate("message-delivered", OrderStatus.DELIVERED),
                        1
                );
            });

            ready.await();
            start.countDown();
            assertEquals(GmailOrderImportService.Outcome.SAVED, shipped.get().outcome());
            assertEquals(GmailOrderImportService.Outcome.SAVED, delivered.get().outcome());
        } finally {
            executor.shutdownNow();
        }

        Order saved = orderRepository.findByUserIdAndMerchantKeyAndOrderNo(
                user.getId(), "amazon.in", "ORDER-123"
        ).orElseThrow();
        assertEquals(1, orderRepository.count());
        assertEquals(OrderStatus.DELIVERED, saved.getStatus());
        assertEquals(
                OrderEmailProcessingStatus.IMPORTED,
                sourceRepository.findByConnectedAccountIdAndGmailMessageId(
                        account.getId(), "message-shipped"
                ).orElseThrow().getProcessingStatus()
        );
        assertEquals(
                OrderEmailProcessingStatus.IMPORTED,
                sourceRepository.findByConnectedAccountIdAndGmailMessageId(
                        account.getId(), "message-delivered"
                ).orElseThrow().getProcessingStatus()
        );
    }

    @Test
    void doesNotGuessMerchantForALegacyOrderFromOrderNumberAlone() {
        User user = new User();
        user.setEmail("legacy@example.com");
        user.setName("Legacy Shopper");
        user.setGoogleId("legacy-google-user");
        user = userRepository.saveAndFlush(user);

        ConnectedAccount account = new ConnectedAccount();
        account.setProvider(ConnectedAccountProvider.GOOGLE);
        account.setEmail("legacy@gmail.com");
        account.setSyncStatus(ConnectedAccountSyncStatus.SYNCED);
        account.setUser(user);
        account = accountRepository.saveAndFlush(account);

        Order legacyOrder = new Order();
        legacyOrder.setUser(user);
        legacyOrder.setBrandName("Unknown legacy merchant");
        legacyOrder.setOrderNo("COLLISION-123");
        legacyOrder.setStatus(OrderStatus.CONFIRMED);
        legacyOrder = orderRepository.saveAndFlush(legacyOrder);

        GmailOrderPreview candidate = new GmailOrderPreview(
                "message-new-merchant",
                "amazon.in",
                "Amazon",
                "COLLISION-123",
                null,
                null,
                null,
                null,
                OrderStatus.SHIPPED,
                List.of()
        );
        GmailOrderImportService.ImportResult result = importService.importOrder(
                account, "message-new-merchant", candidate, 1
        );

        Order unchangedLegacy = orderRepository.findById(legacyOrder.getId()).orElseThrow();
        assertNull(unchangedLegacy.getMerchantKey());
        assertEquals("amazon.in", result.order().getMerchantKey());
        assertNotEquals(legacyOrder.getId(), result.order().getId());
    }

    private GmailOrderPreview candidate(String messageId, OrderStatus status) {
        return new GmailOrderPreview(
                messageId,
                "amazon.in",
                "Amazon",
                "ORDER-123",
                null,
                null,
                null,
                null,
                status,
                List.of()
        );
    }
}
