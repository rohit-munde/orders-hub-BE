package com.indiedev.orders_hub.order.repository;

import com.indiedev.orders_hub.order.entity.Order;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Optional;

public interface OrderRepository extends JpaRepository<Order, Long> {

    Optional<Order> findByUserIdAndCompanyBrandNameAndOrderNo(
            long userId,
            String brandName,
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
    @EntityGraph(attributePaths = "company")
    Page<Order> findPageForUser(@Param("userId") long userId, Pageable pageable);

    @Query("SELECT COUNT(o) FROM Order o WHERE o.user.id = :userId")
    long countByUserId(@Param("userId") long userId);

    void deleteByUserId(long userId);
}
