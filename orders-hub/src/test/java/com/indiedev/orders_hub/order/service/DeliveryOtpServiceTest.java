package com.indiedev.orders_hub.order.service;

import com.indiedev.orders_hub.connectedaccount.entity.ConnectedAccount;
import com.indiedev.orders_hub.connectedaccount.entity.ConnectedAccountProvider;
import com.indiedev.orders_hub.connectedaccount.repository.ConnectedAccountRepository;
import com.indiedev.orders_hub.exception.DeliveryOtpNotFoundException;
import com.indiedev.orders_hub.exception.GmailConnectionRequiredException;
import com.indiedev.orders_hub.gmail.client.GmailApiClient;
import com.indiedev.orders_hub.gmail.dto.GmailMessageContent;
import com.indiedev.orders_hub.gmail.service.GmailOrderParser;
import com.indiedev.orders_hub.gmail.service.GoogleAccessTokenService;
import com.indiedev.orders_hub.order.entity.Order;
import com.indiedev.orders_hub.order.entity.OrderStatus;
import com.indiedev.orders_hub.order.repository.OrderRepository;
import com.indiedev.orders_hub.order.response.DeliveryOtpResponse;
import com.indiedev.orders_hub.order.source.OrderEmailSource;
import com.indiedev.orders_hub.order.source.OrderEmailSourceRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

class DeliveryOtpServiceTest {

    private final OrderRepository orderRepository = mock(OrderRepository.class);
    private final OrderEmailSourceRepository sourceRepository = mock(OrderEmailSourceRepository.class);
    private final ConnectedAccountRepository accountRepository = mock(ConnectedAccountRepository.class);
    private final GoogleAccessTokenService accessTokenService = mock(GoogleAccessTokenService.class);
    private final GmailApiClient gmailApiClient = mock(GmailApiClient.class);
    private final DeliveryOtpService service = new DeliveryOtpService(
            orderRepository,
            sourceRepository,
            accountRepository,
            accessTokenService,
            gmailApiClient,
            new GmailOrderParser()
    );

    private ConnectedAccount account;

    @BeforeEach
    void setUp() {
        account = new ConnectedAccount();
        account.setId(11);
        account.setProvider(ConnectedAccountProvider.GOOGLE);
        when(accessTokenService.getValidAccessToken(account)).thenReturn("access-token");
    }

    @Test
    void returnsTheNewestOtpFromALiveGmailSearch() {
        stubOrder(OrderStatus.OUT_FOR_DELIVERY);
        when(sourceRepository.findAllByOrderId(21)).thenReturn(List.of(source("imported-message")));
        when(gmailApiClient.findMessageIds("access-token", "\"407-3385584\" newer_than:30d", 8))
                .thenReturn(List.of("today-message"));
        stubMessage("today-message", "Delivery OTP is 482913", "2026-08-03T08:15:00Z");
        stubMessage("imported-message", "Your OTP: 1111", "2026-08-01T08:15:00Z");

        DeliveryOtpResponse response = service.findDeliveryOtp(7, 21);

        assertEquals("482913", response.otp());
        assertEquals(Instant.parse("2026-08-03T08:15:00Z"), response.receivedAt());
    }

    @Test
    void fallsBackToTheEmailsTheOrderWasImportedFrom() {
        stubOrder(OrderStatus.SHIPPED);
        when(sourceRepository.findAllByOrderId(21)).thenReturn(List.of(source("imported-message")));
        when(gmailApiClient.findMessageIds(anyString(), anyString(), anyInt())).thenReturn(List.of());
        stubMessage("imported-message", "Share OTP 7254 with the courier", "2026-08-01T08:15:00Z");

        assertEquals("7254", service.findDeliveryOtp(7, 21).otp());
    }

    @Test
    void searchesAllTheUsersInboxesWhenTheOrderHasNoSourceEmails() {
        stubOrder(OrderStatus.SHIPPED);
        when(sourceRepository.findAllByOrderId(21)).thenReturn(List.of());
        when(accountRepository.findAllByUserIdOrderByIdAsc(7)).thenReturn(List.of(account));
        when(gmailApiClient.findMessageIds(anyString(), anyString(), anyInt())).thenReturn(List.of("today-message"));
        stubMessage("today-message", "Delivery OTP: 6402", "2026-08-03T08:15:00Z");

        assertEquals("6402", service.findDeliveryOtp(7, 21).otp());
    }

    @Test
    void explainsWhenNoEmailContainsAnOtp() {
        stubOrder(OrderStatus.OUT_FOR_DELIVERY);
        when(sourceRepository.findAllByOrderId(21)).thenReturn(List.of(source("imported-message")));
        when(gmailApiClient.findMessageIds(anyString(), anyString(), anyInt())).thenReturn(List.of());
        stubMessage("imported-message", "Your order is on its way", "2026-08-01T08:15:00Z");

        DeliveryOtpNotFoundException exception =
                assertThrows(DeliveryOtpNotFoundException.class, () -> service.findDeliveryOtp(7, 21));
        assertEquals("No delivery OTP found in your email yet. Couriers often send it by SMS instead", exception.getMessage());
    }

    @Test
    void doesNotReadGmailForFinishedOrders() {
        stubOrder(OrderStatus.DELIVERED);

        assertThrows(DeliveryOtpNotFoundException.class, () -> service.findDeliveryOtp(7, 21));
        verifyNoInteractions(gmailApiClient, accessTokenService);
    }

    @Test
    void doesNotRevealOtherUsersOrders() {
        when(orderRepository.findByIdAndUserId(21, 7)).thenReturn(Optional.empty());

        assertThrows(DeliveryOtpNotFoundException.class, () -> service.findDeliveryOtp(7, 21));
        verifyNoInteractions(gmailApiClient);
    }

    @Test
    void asksTheUserToReconnectWhenNoGmailIsConnected() {
        stubOrder(OrderStatus.SHIPPED);
        when(sourceRepository.findAllByOrderId(21)).thenReturn(List.of());
        when(accountRepository.findAllByUserIdOrderByIdAsc(7)).thenReturn(List.of());

        assertThrows(GmailConnectionRequiredException.class, () -> service.findDeliveryOtp(7, 21));
    }

    @Test
    void neverPutsUnsafeOrderNumbersIntoAGmailQuery() {
        assertEquals(Optional.of("\"OD12345\" newer_than:30d"), DeliveryOtpService.searchQuery(" OD12345 "));
        assertEquals(Optional.empty(), DeliveryOtpService.searchQuery("A\" OR from:me"));
        assertEquals(Optional.empty(), DeliveryOtpService.searchQuery(null));
    }

    @Test
    void skipsTheSearchButStillReadsSourcesForUnusualOrderNumbers() {
        Order order = stubOrder(OrderStatus.SHIPPED);
        order.setOrderNo("weird \"number\"");
        when(sourceRepository.findAllByOrderId(21)).thenReturn(List.of(source("imported-message")));
        stubMessage("imported-message", "OTP 9081", "2026-08-01T08:15:00Z");

        assertEquals("9081", service.findDeliveryOtp(7, 21).otp());
        verify(gmailApiClient, never()).findMessageIds(anyString(), anyString(), anyInt());
    }

    private Order stubOrder(OrderStatus status) {
        Order order = new Order();
        order.setId(21);
        order.setOrderNo("407-3385584");
        order.setStatus(status);
        when(orderRepository.findByIdAndUserId(21, 7)).thenReturn(Optional.of(order));
        return order;
    }

    private OrderEmailSource source(String messageId) {
        OrderEmailSource source = new OrderEmailSource();
        source.setConnectedAccount(account);
        source.setGmailMessageId(messageId);
        return source;
    }

    private void stubMessage(String id, String body, String receivedAt) {
        when(gmailApiClient.getFullMessage("access-token", id)).thenReturn(new GmailMessageContent(
                id, "Order update", "Amazon <shipment-tracking@amazon.in>", body, Instant.parse(receivedAt)
        ));
    }
}
