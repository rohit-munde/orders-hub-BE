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
import com.indiedev.orders_hub.order.repository.OrderRepository;
import com.indiedev.orders_hub.order.response.DeliveryOtpResponse;
import com.indiedev.orders_hub.order.source.OrderEmailSource;
import com.indiedev.orders_hub.order.source.OrderEmailSourceRepository;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.regex.Pattern;

/**
 * Looks up an order's delivery OTP in the user's Gmail when they ask for it.
 * <p>
 * OTP emails usually arrive on the day of delivery, often after the last sync, so the lookup
 * searches Gmail live for the newest email mentioning the order number, and also re-reads the
 * emails the order was imported from. The OTP is returned to the user and never stored.
 */
@Service
@RequiredArgsConstructor
public class DeliveryOtpService {

    static final int SEARCH_WINDOW_DAYS = 30;
    static final int MAX_MESSAGES_PER_LOOKUP = 8;
    private static final Pattern SAFE_ORDER_NUMBER = Pattern.compile("[A-Za-z0-9][A-Za-z0-9._/#-]{2,63}");
    private static final Logger LOGGER = LoggerFactory.getLogger(DeliveryOtpService.class);

    private final OrderRepository orderRepository;
    private final OrderEmailSourceRepository sourceRepository;
    private final ConnectedAccountRepository accountRepository;
    private final GoogleAccessTokenService accessTokenService;
    private final GmailApiClient gmailApiClient;
    private final GmailOrderParser orderParser;

    public DeliveryOtpResponse findDeliveryOtp(long userId, long orderId) {
        Order order = orderRepository.findByIdAndUserId(orderId, userId)
                .orElseThrow(() -> new DeliveryOtpNotFoundException("Order not found"));
        if (order.getStatus() != null && order.getStatus().isFinal()) {
            throw new DeliveryOtpNotFoundException("This order is already complete, so there is no delivery OTP");
        }

        List<OrderEmailSource> sources = sourceRepository.findAllByOrderId(orderId);
        List<ConnectedAccount> accounts = accountsToSearch(userId, sources);
        if (accounts.isEmpty()) {
            throw new GmailConnectionRequiredException("Gmail account is not connected");
        }

        Optional<DeliveryOtpResponse> newest = Optional.empty();
        for (ConnectedAccount account : accounts) {
            Optional<DeliveryOtpResponse> found = newestOtpIn(account, order.getOrderNo(), sourceMessageIds(sources, account));
            if (found.isPresent() && (newest.isEmpty() || isNewer(found.get(), newest.get()))) {
                newest = found;
            }
        }
        return newest.orElseThrow(() -> new DeliveryOtpNotFoundException(
                "No delivery OTP found in your email yet. Couriers often send it by SMS instead"
        ));
    }

    private Optional<DeliveryOtpResponse> newestOtpIn(
            ConnectedAccount account,
            String orderNo,
            List<String> sourceMessageIds
    ) {
        String accessToken = accessTokenService.getValidAccessToken(account);
        Set<String> messageIds = new LinkedHashSet<>();
        searchQuery(orderNo).ifPresent(query ->
                messageIds.addAll(gmailApiClient.findMessageIds(accessToken, query, MAX_MESSAGES_PER_LOOKUP))
        );
        messageIds.addAll(sourceMessageIds);

        List<String> toRead = messageIds.stream().limit(MAX_MESSAGES_PER_LOOKUP).toList();
        List<GmailMessageContent> messages = readAll(accessToken, toRead);
        return messages.stream()
                .map(message -> orderParser.deliveryOtp(message)
                        .map(otp -> new DeliveryOtpResponse(otp, message.receivedAt())))
                .flatMap(Optional::stream)
                .max(Comparator.comparing(
                        DeliveryOtpResponse::receivedAt,
                        Comparator.nullsFirst(Comparator.naturalOrder())
                ));
    }

    private List<GmailMessageContent> readAll(String accessToken, List<String> messageIds) {
        List<GmailMessageContent> messages = new ArrayList<>();
        try (ExecutorService executor = Executors.newVirtualThreadPerTaskExecutor()) {
            List<Future<GmailMessageContent>> futures = messageIds.stream()
                    .map(id -> executor.submit(() -> gmailApiClient.getFullMessage(accessToken, id)))
                    .toList();
            for (Future<GmailMessageContent> future : futures) {
                try {
                    messages.add(future.get());
                } catch (Exception exception) {
                    if (exception instanceof InterruptedException) {
                        Thread.currentThread().interrupt();
                    }
                    LOGGER.warn("Skipping a Gmail message during OTP lookup: {}", exception.getClass().getSimpleName());
                }
            }
        }
        return messages;
    }

    private List<ConnectedAccount> accountsToSearch(long userId, List<OrderEmailSource> sources) {
        Map<Long, ConnectedAccount> accounts = new LinkedHashMap<>();
        for (OrderEmailSource source : sources) {
            ConnectedAccount account = source.getConnectedAccount();
            if (account != null && account.getProvider() == ConnectedAccountProvider.GOOGLE) {
                accounts.putIfAbsent(account.getId(), account);
            }
        }
        if (accounts.isEmpty()) {
            accountRepository.findAllByUserIdOrderByIdAsc(userId).stream()
                    .filter(account -> account.getProvider() == ConnectedAccountProvider.GOOGLE)
                    .forEach(account -> accounts.putIfAbsent(account.getId(), account));
        }
        return List.copyOf(accounts.values());
    }

    private List<String> sourceMessageIds(List<OrderEmailSource> sources, ConnectedAccount account) {
        return sources.stream()
                .filter(source -> source.getConnectedAccount() != null
                        && source.getConnectedAccount().getId() == account.getId())
                .map(OrderEmailSource::getGmailMessageId)
                .filter(StringUtils::hasText)
                .toList();
    }

    static Optional<String> searchQuery(String orderNo) {
        if (!StringUtils.hasText(orderNo) || !SAFE_ORDER_NUMBER.matcher(orderNo.strip()).matches()) {
            return Optional.empty();
        }
        return Optional.of("\"" + orderNo.strip() + "\" newer_than:" + SEARCH_WINDOW_DAYS + "d");
    }

    private boolean isNewer(DeliveryOtpResponse candidate, DeliveryOtpResponse current) {
        Instant candidateTime = candidate.receivedAt();
        Instant currentTime = current.receivedAt();
        return candidateTime != null && (currentTime == null || candidateTime.isAfter(currentTime));
    }
}
