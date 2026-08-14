package com.indiedev.orders_hub.connectedaccount.service;

import com.indiedev.orders_hub.connectedaccount.entity.ConnectedAccount;
import com.indiedev.orders_hub.connectedaccount.entity.ConnectedAccountProvider;
import com.indiedev.orders_hub.connectedaccount.repository.ConnectedAccountRepository;
import com.indiedev.orders_hub.connectedaccount.response.DisconnectAccountResponse;
import com.indiedev.orders_hub.exception.LastConnectedGoogleAccountException;
import com.indiedev.orders_hub.gmail.service.GmailConnectionService;
import com.indiedev.orders_hub.gmail.service.GoogleOAuthService;
import com.indiedev.orders_hub.order.repository.OrderRepository;
import com.indiedev.orders_hub.order.source.OrderEmailSourceRepository;
import com.indiedev.orders_hub.user.repository.UserRepository;
import org.junit.jupiter.api.Test;
import org.mockito.InOrder;

import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class ConnectedAccountServiceTest {

    @Test
    void disconnectsAGoogleAccountAndDeletesImportedOrderData() {
        ConnectedAccountRepository connectedAccountRepository = mock(ConnectedAccountRepository.class);
        OrderEmailSourceRepository orderEmailSourceRepository = mock(OrderEmailSourceRepository.class);
        OrderRepository orderRepository = mock(OrderRepository.class);
        ConnectedAccountService service = service(
                connectedAccountRepository,
                orderEmailSourceRepository,
                orderRepository
        );
        ConnectedAccount account = googleAccount(9, "shopper@gmail.com");
        when(connectedAccountRepository.findByIdAndUserIdAndProvider(
                9,
                7,
                ConnectedAccountProvider.GOOGLE
        )).thenReturn(Optional.of(account));
        when(connectedAccountRepository.countByUserIdAndProvider(7, ConnectedAccountProvider.GOOGLE))
                .thenReturn(2L);
        when(orderEmailSourceRepository.countByConnectedAccountUserId(7)).thenReturn(5L);
        when(orderRepository.countByUserId(7)).thenReturn(3L);

        DisconnectAccountResponse response = service.disconnectGoogle(7, 9);

        assertEquals(true, response.connectedAccountDeleted());
        assertEquals(3, response.ordersDeleted());
        assertEquals(5, response.emailSourcesDeleted());
        assertEquals("shopper@gmail.com", response.disconnectedEmailId());
        InOrder deleteOrder = inOrder(orderEmailSourceRepository, orderRepository, connectedAccountRepository);
        deleteOrder.verify(orderEmailSourceRepository).deleteByConnectedAccountUserId(7);
        deleteOrder.verify(orderRepository).deleteByUserId(7);
        deleteOrder.verify(connectedAccountRepository).delete(account);
    }

    @Test
    void refusesToDisconnectTheUsersLastGoogleAccount() {
        ConnectedAccountRepository connectedAccountRepository = mock(ConnectedAccountRepository.class);
        OrderEmailSourceRepository orderEmailSourceRepository = mock(OrderEmailSourceRepository.class);
        OrderRepository orderRepository = mock(OrderRepository.class);
        ConnectedAccountService service = service(
                connectedAccountRepository,
                orderEmailSourceRepository,
                orderRepository
        );
        ConnectedAccount account = googleAccount(9, "shopper@gmail.com");
        when(connectedAccountRepository.findByIdAndUserIdAndProvider(
                9,
                7,
                ConnectedAccountProvider.GOOGLE
        )).thenReturn(Optional.of(account));
        when(connectedAccountRepository.countByUserIdAndProvider(7, ConnectedAccountProvider.GOOGLE))
                .thenReturn(1L);

        assertThrows(LastConnectedGoogleAccountException.class, () -> service.disconnectGoogle(7, 9));

        verify(orderEmailSourceRepository, never()).deleteByConnectedAccountUserId(7);
        verify(orderRepository, never()).deleteByUserId(7);
        verify(connectedAccountRepository, never()).delete(account);
    }

    private ConnectedAccountService service(
            ConnectedAccountRepository connectedAccountRepository,
            OrderEmailSourceRepository orderEmailSourceRepository,
            OrderRepository orderRepository
    ) {
        return new ConnectedAccountService(
                mock(UserRepository.class),
                mock(GoogleOAuthService.class),
                mock(GmailConnectionService.class),
                connectedAccountRepository,
                orderEmailSourceRepository,
                orderRepository
        );
    }

    private ConnectedAccount googleAccount(long id, String email) {
        ConnectedAccount account = new ConnectedAccount();
        account.setId(id);
        account.setProvider(ConnectedAccountProvider.GOOGLE);
        account.setEmail(email);
        return account;
    }
}
