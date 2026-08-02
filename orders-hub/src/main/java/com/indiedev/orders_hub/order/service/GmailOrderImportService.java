package com.indiedev.orders_hub.order.service;

import com.indiedev.orders_hub.connectedaccount.ConnectedAccount;
import com.indiedev.orders_hub.gmail.dto.GmailOrderPreview;
import com.indiedev.orders_hub.order.Order;
import com.indiedev.orders_hub.order.OrderRepository;
import com.indiedev.orders_hub.order.OrderStatus;
import com.indiedev.orders_hub.order.source.OrderEmailProcessingStatus;
import com.indiedev.orders_hub.order.source.OrderEmailSource;
import com.indiedev.orders_hub.order.source.OrderEmailSourceRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.time.Instant;
import java.util.Locale;
import java.util.Optional;

@Service
@RequiredArgsConstructor
public class GmailOrderImportService {

    private static final String INVALID_CANDIDATE = "Missing merchant or order number";
    private static final String IMPORT_FAILURE = "Unable to import Gmail message";

    private final OrderRepository orderRepository;
    private final OrderEmailSourceRepository sourceRepository;

    @Transactional(readOnly = true)
    public boolean shouldProcess(long accountId, String gmailMessageId, int parserVersion) {
        return sourceRepository.findByConnectedAccountIdAndGmailMessageId(accountId, gmailMessageId)
                .map(source -> isRetryable(source, parserVersion))
                .orElse(true);
    }

    @Transactional
    public ImportResult importOrder(
            ConnectedAccount account,
            GmailOrderPreview candidate,
            int parserVersion
    ) {
        Optional<OrderEmailSource> existingSource = findSource(account, candidate.gmailMessageId());
        if (existingSource.isPresent() && !isRetryable(existingSource.get(), parserVersion)) {
            return new ImportResult(Outcome.SKIPPED, existingSource.get().getOrder());
        }

        if (!hasIdentity(candidate)) {
            saveSource(
                    existingSource.orElseGet(OrderEmailSource::new),
                    account,
                    candidate.gmailMessageId(),
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
                .orElseGet(() -> newOrder(account, merchantKey, orderNo));
        merge(order, candidate);
        order = orderRepository.save(order);

        saveSource(
                existingSource.orElseGet(OrderEmailSource::new),
                account,
                candidate.gmailMessageId(),
                order,
                OrderEmailProcessingStatus.IMPORTED,
                null,
                parserVersion
        );
        return new ImportResult(Outcome.SAVED, order);
    }

    @Transactional
    public void recordFailure(ConnectedAccount account, String gmailMessageId, int parserVersion) {
        OrderEmailSource source = findSource(account, gmailMessageId).orElseGet(OrderEmailSource::new);
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

    private boolean isRetryable(OrderEmailSource source, int parserVersion) {
        return source.getProcessingStatus() == OrderEmailProcessingStatus.FAILED
                || source.getProcessingStatus() == OrderEmailProcessingStatus.IGNORED
                && source.getParserVersion() < parserVersion;
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
