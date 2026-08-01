package com.indiedev.orders_hub.order;

import com.indiedev.orders_hub.common.BaseEntity;
import com.indiedev.orders_hub.user.User;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;

@Getter
@Setter
@Entity
@Table(name = "orders")
public class Order extends BaseEntity {

    @Id
    @GeneratedValue(strategy = jakarta.persistence.GenerationType.IDENTITY)
    private long id;

    @Column(nullable = true, length = 255)
    private String brandName;

    @Column(nullable = false, length = 255)
    private String orderNo;

    @Column(nullable = true)
    private BigDecimal billAmount;

    @Column
    private boolean isPaid;

    @Column
    private String otp;

    @Column(nullable = false)
    private String status;

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
