package com.indiedev.orders_hub.order;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Optional;

public interface OrderRepository extends JpaRepository<Order, Long> {

    Optional<Order> findByUserIdAndMerchantKeyAndOrderNo(
            long userId,
            String merchantKey,
            String orderNo
    );

    @Query(
            value = """
                    select orderRecord
                    from Order orderRecord
                    where orderRecord.user.id = :userId
                    order by
                        case when orderRecord.placedAt is null then 1 else 0 end,
                        orderRecord.placedAt desc,
                        orderRecord.id desc
                    """,
            countQuery = """
                    select count(orderRecord)
                    from Order orderRecord
                    where orderRecord.user.id = :userId
                    """
    )
    Page<Order> findPageForUser(@Param("userId") long userId, Pageable pageable);
}
