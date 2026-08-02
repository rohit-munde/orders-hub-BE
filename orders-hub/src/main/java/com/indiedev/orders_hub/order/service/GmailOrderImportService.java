package com.indiedev.orders_hub.order.service;

import com.indiedev.orders_hub.connectedaccount.ConnectedAccount;
import com.indiedev.orders_hub.gmail.dto.GmailOrderPreview;
import com.indiedev.orders_hub.order.Order;
import com.indiedev.orders_hub.order.OrderItem;
import com.indiedev.orders_hub.order.OrderRepository;
import com.indiedev.orders_hub.order.OrderStatus;
import com.indiedev.orders_hub.order.source.OrderEmailProcessingStatus;
import com.indiedev.orders_hub.order.source.OrderEmailSource;
import com.indiedev.orders_hub.order.source.OrderEmailSourceRepository;
import com.indiedev.orders_hub.user.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.time.Instant;
import java.util.List;
import java.util.Locale;
import java.util.Optional;

@Service
@RequiredArgsConstructor
public class GmailOrderImportService {

    private static final String INVALID_CANDIDATE = "Missing merchant or order number";
    private static final String MESSAGE_ID_MISMATCH = "Gmail message identity mismatch";
    private static final String IMPORT_FAILURE = "Unable to import Gmail message";

    private final OrderRepository orderRepository;
    private final OrderEmailSourceRepository sourceRepository;
    private final UserRepository userRepository;

    @Transactional(readOnly = true)
    public boolean shouldProcess(long accountId, String gmailMessageId, int parserVersion) {
        return sourceRepository.findByConnectedAccountIdAndGmailMessageId(accountId, gmailMessageId)
                .map(source -> isRetryable(source, parserVersion))
                .orElse(true);
    }

    @Transactional
    public ImportResult importOrder(
            ConnectedAccount account,
            String gmailMessageId,
            GmailOrderPreview candidate,
            int parserVersion
    ) {
        lockUser(account);
        Optional<OrderEmailSource> existingSource = findSource(account, gmailMessageId);
        if (existingSource.isPresent() && !isRetryable(existingSource.get(), parserVersion)) {
            return new ImportResult(Outcome.SKIPPED, existingSource.get().getOrder());
        }

        if (!gmailMessageId.equals(candidate.gmailMessageId())) {
            saveSource(
                    existingSource.orElseGet(OrderEmailSource::new),
                    account,
                    gmailMessageId,
                    null,
                    OrderEmailProcessingStatus.IGNORED,
                    MESSAGE_ID_MISMATCH,
                    parserVersion
            );
            return new ImportResult(Outcome.IGNORED, null);
        }

        if (!hasIdentity(candidate)) {
            saveSource(
                    existingSource.orElseGet(OrderEmailSource::new),
                    account,
                    gmailMessageId,
                    null,
                    OrderEmailProcessingStatus.IGNORED,
                    INVALID_CANDIDATE,
                    parserVersion
            );
            return new ImportResult(Outcome.IGNORED, null);
        }

        String merchantKey = candidate.merchantKey().strip().toLowerCase(Locale.ROOT);
        String orderNo = candidate.orderNo().strip().toUpperCase(Locale.ROOT);
        Order order = orderRepository.findByUserIdAndMerchantKeyAndOrderNo(
                        account.getUser().getId(), merchantKey, orderNo
                )
                .or(() -> findLegacyOrder(account, orderNo))
                .orElseGet(() -> newOrder(account, merchantKey, orderNo));
        if (!StringUtils.hasText(order.getMerchantKey())) {
            order.setMerchantKey(merchantKey);
        }
        merge(order, candidate);
        order = orderRepository.save(order);

        saveSource(
                existingSource.orElseGet(OrderEmailSource::new),
                account,
                gmailMessageId,
                order,
                OrderEmailProcessingStatus.IMPORTED,
                null,
                parserVersion
        );
        return new ImportResult(Outcome.SAVED, order);
    }

    @Transactional
    public void recordFailure(ConnectedAccount account, String gmailMessageId, int parserVersion) {
        lockUser(account);
        Optional<OrderEmailSource> existingSource = findSource(account, gmailMessageId);
        if (existingSource.isPresent() && !isRetryable(existingSource.get(), parserVersion)) {
            return;
        }
        OrderEmailSource source = existingSource.orElseGet(OrderEmailSource::new);
        saveSource(
                source,
                account,
                gmailMessageId,
                null,
                OrderEmailProcessingStatus.FAILED,
                IMPORT_FAILURE,
                parserVersion
        );
    }

    private Optional<OrderEmailSource> findSource(ConnectedAccount account, String gmailMessageId) {
        return sourceRepository.findByConnectedAccountIdAndGmailMessageId(
                account.getId(), gmailMessageId
        );
    }

    private void lockUser(ConnectedAccount account) {
        userRepository.findByIdForUpdate(account.getUser().getId())
                .orElseThrow(() -> new IllegalStateException("Connected account user no longer exists"));
    }

    private boolean isRetryable(OrderEmailSource source, int parserVersion) {
        return source.getProcessingStatus() == OrderEmailProcessingStatus.FAILED
                || (source.getProcessingStatus() == OrderEmailProcessingStatus.IGNORED
                    && source.getParserVersion() < parserVersion);
    }

    private boolean hasIdentity(GmailOrderPreview candidate) {
        return StringUtils.hasText(candidate.gmailMessageId())
                && StringUtils.hasText(candidate.merchantKey())
                && StringUtils.hasText(candidate.orderNo());
    }

    private Order newOrder(ConnectedAccount account, String merchantKey, String orderNo) {
        Order order = new Order();
        order.setUser(account.getUser());
        order.setMerchantKey(merchantKey);
        order.setOrderNo(orderNo);
        order.setStatus(OrderStatus.UNKNOWN);
        return order;
    }

    private Optional<Order> findLegacyOrder(ConnectedAccount account, String orderNo) {
        List<Order> matches = orderRepository.findAllByUserIdAndOrderNo(
                        account.getUser().getId(), orderNo
                ).stream()
                .filter(order -> !StringUtils.hasText(order.getMerchantKey()))
                .toList();
        return matches.size() == 1 ? Optional.of(matches.getFirst()) : Optional.empty();
    }

    private void merge(Order order, GmailOrderPreview candidate) {
        if (!StringUtils.hasText(order.getBrandName()) && StringUtils.hasText(candidate.brandName())) {
            order.setBrandName(candidate.brandName().strip());
        }
        if (order.getBillAmount() == null && candidate.billAmount() != null) {
            order.setBillAmount(candidate.billAmount());
        }
        if (!StringUtils.hasText(order.getCurrency()) && StringUtils.hasText(candidate.currency())) {
            order.setCurrency(candidate.currency().strip().toUpperCase(Locale.ROOT));
        }
        if (candidate.paid() != null && (order.getPaid() == null || candidate.paid())) {
            order.setPaid(candidate.paid());
        }
        if (candidate.status() != null && candidate.status().ordinal() > order.getStatus().ordinal()) {
            order.setStatus(candidate.status());
        }
        if (order.getOrderItems().isEmpty()) {
            candidate.orderItems().stream()
                    .filter(item -> StringUtils.hasText(item.productName()))
                    .map(item -> orderItem(order, item))
                    .forEach(order.getOrderItems()::add);
        }
    }

    private OrderItem orderItem(Order order, GmailOrderPreview.OrderItemPreview preview) {
        OrderItem item = new OrderItem();
        item.setOrder(order);
        item.setProductName(preview.productName().strip());
        item.setProductUrl(StringUtils.hasText(preview.productUrl()) ? preview.productUrl().strip() : null);
        item.setQuantity(preview.quantity());
        item.setPrice(preview.price());
        return item;
    }

    private void saveSource(
            OrderEmailSource source,
            ConnectedAccount account,
            String gmailMessageId,
            Order order,
            OrderEmailProcessingStatus status,
            String failureReason,
            int parserVersion
    ) {
        source.setConnectedAccount(account);
        source.setGmailMessageId(gmailMessageId);
        source.setOrder(order);
        source.setProcessingStatus(status);
        source.setFailureReason(failureReason);
        source.setParserVersion(parserVersion);
        source.setProcessedAt(Instant.now());
        sourceRepository.save(source);
    }

    public record ImportResult(Outcome outcome, Order order) {
    }

    public enum Outcome {
        SAVED,
        IGNORED,
        SKIPPED
    }
}
