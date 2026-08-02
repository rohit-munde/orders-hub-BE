package com.indiedev.orders_hub.order.source;

import com.indiedev.orders_hub.common.BaseEntity;
import com.indiedev.orders_hub.connectedaccount.ConnectedAccount;
import com.indiedev.orders_hub.order.Order;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

import java.time.Instant;

@Getter
@Setter
@Entity
@Table(
        name = "order_email_sources",
        uniqueConstraints = @UniqueConstraint(
                name = "uk_order_email_account_message",
                columnNames = {"connected_account_id", "gmail_message_id"}
        )
)
public class OrderEmailSource extends BaseEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(
            name = "connected_account_id",
            nullable = false,
            foreignKey = @ForeignKey(name = "fk_order_email_connected_account")
    )
    private ConnectedAccount connectedAccount;

    @Column(name = "gmail_message_id", nullable = false, length = 255)
    private String gmailMessageId;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "order_id", foreignKey = @ForeignKey(name = "fk_order_email_order"))
    private Order order;

    @Enumerated(EnumType.STRING)
    @Column(name = "processing_status", nullable = false, length = 32)
    private OrderEmailProcessingStatus processingStatus;

    @Column(name = "failure_reason", length = 500)
    private String failureReason;

    @Column(name = "parser_version", nullable = false)
    private int parserVersion;

    @Column(name = "processed_at", nullable = false)
    private Instant processedAt;
}
