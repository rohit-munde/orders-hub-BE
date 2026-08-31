package com.indiedev.orders_hub.order.service;

import com.indiedev.orders_hub.connectedaccount.entity.ConnectedAccount;
import com.indiedev.orders_hub.gmail.dto.GmailOrderPreview;
import com.indiedev.orders_hub.order.entity.Company;
import com.indiedev.orders_hub.order.entity.Order;
import com.indiedev.orders_hub.order.entity.OrderItem;
import com.indiedev.orders_hub.order.event.CompanyCreatedEvent;
import com.indiedev.orders_hub.order.repository.OrderRepository;
import com.indiedev.orders_hub.order.entity.OrderStatus;
import com.indiedev.orders_hub.order.source.OrderEmailProcessingStatus;
import com.indiedev.orders_hub.order.source.OrderEmailSource;
import com.indiedev.orders_hub.order.source.OrderEmailSourceRepository;
import com.indiedev.orders_hub.user.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.time.Instant;
import java.util.Locale;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicBoolean;

@Service
@RequiredArgsConstructor
public class GmailOrderImportService {

    private static final String INVALID_CANDIDATE = "Missing order number";
    private static final String MESSAGE_ID_MISMATCH = "Gmail message identity mismatch";
    private static final String IMPORT_FAILURE = "Unable to import Gmail message";

    private final OrderRepository orderRepository;
    private final OrderEmailSourceRepository sourceRepository;
    private final UserRepository userRepository;
    private final ApplicationEventPublisher eventPublisher;
    private final CompanyMasterService companyMasterService;

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
            Order staleOrder = existingSource.map(OrderEmailSource::getOrder).orElse(null);
            saveSource(
                    existingSource.orElseGet(OrderEmailSource::new),
                    account,
                    gmailMessageId,
                    null,
                    OrderEmailProcessingStatus.IGNORED,
                    INVALID_CANDIDATE,
                    parserVersion
            );
            existingSource.ifPresent(source -> deleteStaleOrderIfUnreferenced(source, staleOrder));
            return new ImportResult(Outcome.IGNORED, null);
        }

        String brandName = StringUtils.hasText(candidate.brandName()) ? candidate.brandName().strip() : null;
        String orderNo = candidate.orderNo().strip().toUpperCase(Locale.ROOT);
        if (existingSource.isPresent() && !isRetryable(existingSource.get(), parserVersion)
                && representsOrder(existingSource.get(), orderNo)) {
            return new ImportResult(Outcome.SKIPPED, existingSource.get().getOrder());
        }

        Order staleOrder = existingSource.map(OrderEmailSource::getOrder).orElse(null);
        AtomicBoolean createdNewCompany = new AtomicBoolean(false);
        Order order = existingSource
                .filter(source -> representsOrder(source, orderNo))
                .map(OrderEmailSource::getOrder)
                .or(() -> (brandName == null
                        ? Optional.<Order>empty()
                        : orderRepository.findByUserIdAndCompanyBrandNameAndOrderNo(
                                account.getUser().getId(), brandName, orderNo
                        )))
                .orElseGet(() -> {
                    createdNewCompany.set(true);
                    return newOrder(account, brandName, orderNo, createdNewCompany);
                });
        merge(order, candidate);
        order = orderRepository.save(order);

        if (createdNewCompany.get() && order.getCompany() != null && order.getCompany().getId() != 0) {
            eventPublisher.publishEvent(new CompanyCreatedEvent(
                    order.getCompany().getId(),
                    brandName,
                    null,
                    null
            ));
        }

        saveSource(
                existingSource.orElseGet(OrderEmailSource::new),
                account,
                gmailMessageId,
                order,
                OrderEmailProcessingStatus.IMPORTED,
                null,
                parserVersion
        );
        if (existingSource.isPresent()) {
            deleteReassignedStaleOrderIfUnreferenced(existingSource.get(), staleOrder, order);
        }
        return new ImportResult(Outcome.SAVED, order);
    }

    @Transactional
    public void recordFailure(ConnectedAccount account, String gmailMessageId, int parserVersion) {
        lockUser(account);
        Optional<OrderEmailSource> existingSource = findSource(account, gmailMessageId);
        if (existingSource.isPresent()
                && existingSource.get().getProcessingStatus() == OrderEmailProcessingStatus.IMPORTED) {
            return;
        }
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
                || source.getParserVersion() < parserVersion;
    }

    private boolean hasIdentity(GmailOrderPreview candidate) {
        return StringUtils.hasText(candidate.gmailMessageId())
                && StringUtils.hasText(candidate.orderNo());
    }

    private boolean representsOrder(OrderEmailSource source, String orderNo) {
        Order order = source.getOrder();
        return order != null
                && orderNo.equals(order.getOrderNo());
    }

    private void deleteStaleOrderIfUnreferenced(OrderEmailSource source, Order staleOrder) {
        if (staleOrder == null || staleOrder.getId() == 0) {
            return;
        }
        if (!sourceRepository.existsByOrderIdAndIdNot(staleOrder.getId(), source.getId())) {
            orderRepository.delete(staleOrder);
        }
    }

    private void deleteReassignedStaleOrderIfUnreferenced(
            OrderEmailSource source,
            Order staleOrder,
            Order currentOrder
    ) {
        if (sameOrder(staleOrder, currentOrder)) {
            return;
        }
        deleteStaleOrderIfUnreferenced(source, staleOrder);
    }

    private boolean sameOrder(Order first, Order second) {
        if (first == second) {
            return true;
        }
        return first != null && second != null && first.getId() != 0 && first.getId() == second.getId();
    }

    private Order newOrder(
            ConnectedAccount account,
            String brandName,
            String orderNo,
            AtomicBoolean createdNewCompany
    ) {
        Order order = new Order();
        order.setUser(account.getUser());
        order.setOrderNo(orderNo);
        order.setStatus(OrderStatus.UNKNOWN);
        CompanyMasterService.FindOrCreateCompanyResult companyResult =
                companyMasterService.findOrCreateByBrandName(brandName);
        createdNewCompany.set(companyResult.created());
        order.setCompany(companyResult.company());
        return order;
    }

    private void merge(Order order, GmailOrderPreview candidate) {
        if (order.getCompany() == null) {
            order.setCompany(new Company());
        }
        if (!StringUtils.hasText(order.getCompany().getBrandName()) && StringUtils.hasText(candidate.brandName())) {
            order.getCompany().setBrandName(candidate.brandName().strip());
        }
        if (candidate.status() == OrderStatus.REFUNDED) {
            if (candidate.billAmount() != null) {
                order.setRefundAmount(candidate.billAmount());
            }
        } else {
            if (order.getBillAmount() == null && candidate.billAmount() != null) {
                order.setBillAmount(candidate.billAmount());
            }
        }
        if (!StringUtils.hasText(order.getCurrency()) && StringUtils.hasText(candidate.currency())) {
            order.setCurrency(candidate.currency().strip().toUpperCase(Locale.ROOT));
        }
        if (candidate.paid() != null && (order.getPaid() == null || candidate.paid())) {
            order.setPaid(candidate.paid());
        }
        if (candidate.placedAt() != null) {
            if (order.getPlacedAt() == null || candidate.placedAt().isAfter(order.getPlacedAt())) {
                order.setPlacedAt(candidate.placedAt());
                if (candidate.status() != null && candidate.status() != OrderStatus.UNKNOWN) {
                    order.setStatus(candidate.status());
                }
            } else if (order.getStatus() == OrderStatus.UNKNOWN && candidate.status() != null) {
                order.setStatus(candidate.status());
            }
        } else {
            if (candidate.status() != null && candidate.status().ordinal() > order.getStatus().ordinal()) {
                order.setStatus(candidate.status());
            }
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
