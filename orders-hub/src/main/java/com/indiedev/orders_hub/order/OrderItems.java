package com.indiedev.orders_hub.order;

import com.indiedev.orders_hub.common.BaseEnity;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

import java.math.BigDecimal;

@Getter
@Setter
@Entity(name = "order_items")
public class OrderItems extends BaseEnity {

    @Id
    @GeneratedValue(strategy = jakarta.persistence.GenerationType.IDENTITY)
    private long id;

    @Column(nullable = false, length = 255)
    private String productName;

    @Column(nullable = true, length = 2048)
    private String productUrl;

    @Column
    private int quantity;

    @Column
    private BigDecimal price;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "order_id", nullable = false, foreignKey = @ForeignKey(name = "fk_order_items"))
    private Order order;
}
