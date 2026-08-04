package com.indiedev.orders_hub.order;

import com.indiedev.orders_hub.common.BaseEntity;
import com.indiedev.orders_hub.user.User;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

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
                name = "uk_order_user_merchant_number",
                columnNames = {"user_id", "merchant_key", "order_no"}
        )
)
public class Order extends BaseEntity {

    @Id
    @GeneratedValue(strategy = jakarta.persistence.GenerationType.IDENTITY)
    private long id;

    @Column(nullable = true, length = 255)
    private String brandName;

    @Column(name = "merchant_key", length = 255)
    private String merchantKey;

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

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id", nullable = false, foreignKey = @ForeignKey(name = "fk_connected_order"))
    private User user;

    @OneToMany(
            mappedBy = "order",
            cascade = CascadeType.ALL,
            orphanRemoval = true
    )
    private List<OrderItem> orderItems = new ArrayList<>();
}
