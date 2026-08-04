package com.indiedev.orders_hub.order;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

public interface OrderRepository extends JpaRepository<Order, Long> {

    Optional<Order> findByUserIdAndMerchantKeyAndOrderNo(
            long userId,
            String merchantKey,
            String orderNo
    );

    @EntityGraph(attributePaths = "orderItems")
    @Query("""
            select orderRecord
            from Order orderRecord
            where orderRecord.user.id = :userId
            order by
                case when orderRecord.placedAt is null then 1 else 0 end,
                orderRecord.placedAt desc,
                orderRecord.id desc
            """)
    List<Order> findAllForUserNewestFirst(@Param("userId") long userId);
}
