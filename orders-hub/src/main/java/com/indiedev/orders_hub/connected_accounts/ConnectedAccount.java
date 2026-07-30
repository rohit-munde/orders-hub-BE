package com.indiedev.orders_hub.connected_accounts;

import com.indiedev.orders_hub.common.BaseEnity;
import com.indiedev.orders_hub.user.User;
import jakarta.persistence.*;

@Entity(name = "connected_accounts")
public class ConnectedAccount extends BaseEnity {

    @Id
    @GeneratedValue(strategy = jakarta.persistence.GenerationType.IDENTITY)
    private long id;

    @Column(nullable = false, unique = true, length = 255)
    private String email;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id", nullable = false, foreignKey = @ForeignKey(name = "fk_connected_account_user"))
    private User user;
}
