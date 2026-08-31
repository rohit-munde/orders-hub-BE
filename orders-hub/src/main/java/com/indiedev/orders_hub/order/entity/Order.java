package com.indiedev.orders_hub.order.entity;

import com.indiedev.orders_hub.common.BaseEntity;
import com.indiedev.orders_hub.user.entity.User;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;
import org.hibernate.annotations.BatchSize;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

@Getter
@Setter
@Entity
@Table(
        name = "orders",
        uniqueConstraints = @UniqueConstraint(
                name = "uk_order_user_company_number",
                columnNames = {"user_id", "company_id", "order_no"}
        )
)
public class Order extends BaseEntity {

    @Id
    @GeneratedValue(strategy = jakarta.persistence.GenerationType.IDENTITY)
    private long id;

    @Column(name = "order_no", nullable = false, length = 255)
    private String orderNo;

    @Column(nullable = true)
    private BigDecimal billAmount;

    @Column(length = 3)
    private String currency;

    @Column(name = "is_paid")
    private Boolean paid;

    @Column
    private String otp;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 32)
    private OrderStatus status;

    @Column(name = "placed_at")
    private Instant placedAt;

    @Column(name = "refund_amount", nullable = true)
    private BigDecimal refundAmount;

    @ManyToOne(fetch = FetchType.EAGER)
    @JoinColumn(name = "user_id", nullable = false, foreignKey = @ForeignKey(name = "fk_connected_order"))
    private User user;

    @OneToMany(
            mappedBy = "order",
            cascade = CascadeType.ALL,
            orphanRemoval = true,
            fetch = FetchType.EAGER
    )
    @BatchSize(size = 50)
    private List<OrderItem> orderItems = new ArrayList<>();

    @ManyToOne(fetch = FetchType.LAZY, cascade = {CascadeType.PERSIST, CascadeType.MERGE})
    @JoinColumn(
            name = "company_id",
            nullable = true,
            foreignKey = @ForeignKey(name = "fk_orders_company")
    )
    private Company company;
}
