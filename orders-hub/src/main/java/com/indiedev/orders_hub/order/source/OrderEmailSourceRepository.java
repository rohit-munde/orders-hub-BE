package com.indiedev.orders_hub.order.source;

import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface OrderEmailSourceRepository extends JpaRepository<OrderEmailSource, Long> {

    Optional<OrderEmailSource> findByConnectedAccountIdAndGmailMessageId(
            long connectedAccountId,
            String gmailMessageId
    );

    boolean existsByOrderIdAndIdNot(long orderId, long sourceId);

    long countByConnectedAccountId(long connectedAccountId);

    void deleteByConnectedAccountId(long connectedAccountId);

    long countByConnectedAccountUserId(long userId);

    void deleteByConnectedAccountUserId(long userId);

    @EntityGraph(attributePaths = "connectedAccount")
    List<OrderEmailSource> findAllByOrderId(long orderId);
}
