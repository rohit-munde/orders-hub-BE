package com.indiedev.orders_hub.order.source;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface OrderEmailSourceRepository extends JpaRepository<OrderEmailSource, Long> {

    Optional<OrderEmailSource> findByConnectedAccountIdAndGmailMessageId(
            long connectedAccountId,
            String gmailMessageId
    );
}
