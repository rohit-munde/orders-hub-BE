package com.indiedev.orders_hub.order;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface OrderRepository extends JpaRepository<Order, Long> {

    Optional<Order> findByUserIdAndMerchantKeyAndOrderNo(
            long userId,
            String merchantKey,
            String orderNo
    );
}
