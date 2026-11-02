package com.indiedev.orders_hub.user.service;

import com.indiedev.orders_hub.connectedaccount.entity.ConnectedAccount;
import com.indiedev.orders_hub.connectedaccount.repository.ConnectedAccountRepository;
import com.indiedev.orders_hub.exception.AuthenticatedUserMissingException;
import com.indiedev.orders_hub.gmail.service.GoogleAccessRevocationService;
import com.indiedev.orders_hub.order.repository.OrderRepository;
import com.indiedev.orders_hub.order.source.OrderEmailSourceRepository;
import com.indiedev.orders_hub.user.entity.User;
import com.indiedev.orders_hub.user.repository.UserRepository;
import com.indiedev.orders_hub.user.response.AccountDeletionResponse;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

/**
 * Permanently deletes a user's OrdersHub account and every piece of personal data tied to it:
 * imported orders and their items, Gmail message references, connected accounts with their
 * encrypted Google tokens, and the user record itself. Google access is revoked once the
 * deletion has committed.
 * <p>
 * Shared merchant reference data (company names, domains and logos) is not personal data and
 * is kept.
 */
@Service
@RequiredArgsConstructor
public class AccountDeletionService {

    private static final Logger LOGGER = LoggerFactory.getLogger(AccountDeletionService.class);

    private final UserRepository userRepository;
    private final ConnectedAccountRepository connectedAccountRepository;
    private final OrderEmailSourceRepository orderEmailSourceRepository;
    private final OrderRepository orderRepository;
    private final GoogleAccessRevocationService googleAccessRevocationService;

    @Transactional
    public AccountDeletionResponse deleteAccount(long userId) {
        User user = userRepository.findByIdForUpdate(userId)
                .orElseThrow(AuthenticatedUserMissingException::new);
        List<ConnectedAccount> accounts = connectedAccountRepository.findAllByUserIdOrderByIdAsc(userId);
        long ordersDeleted = orderRepository.countByUserId(userId);

        googleAccessRevocationService.revokeAfterCommit(accounts);

        orderEmailSourceRepository.deleteByConnectedAccountUserId(userId);
        orderRepository.deleteByUserId(userId);
        connectedAccountRepository.deleteAll(accounts);
        user.getConnectedAccounts().clear();
        user.getOrders().clear();
        userRepository.delete(user);

        LOGGER.info(
                "Deleted user account: userId={}, connectedAccounts={}, orders={}",
                userId,
                accounts.size(),
                ordersDeleted
        );
        return new AccountDeletionResponse(true, accounts.size(), ordersDeleted);
    }
}
