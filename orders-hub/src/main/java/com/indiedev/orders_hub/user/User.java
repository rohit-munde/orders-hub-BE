package com.indiedev.orders_hub.user;

import com.indiedev.orders_hub.common.BaseEnity;
import com.indiedev.orders_hub.connected_accounts.ConnectedAccount;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

import java.util.ArrayList;
import java.util.List;

@Getter
@Setter
@Entity(name = "users")
public class User extends BaseEnity {

    @Id
    @GeneratedValue(strategy = jakarta.persistence.GenerationType.IDENTITY)
    private long id;

    @Column(nullable = false, unique = true, length = 255)
    private String email;

    @Column(nullable = false, length = 100)
    private String name;

    @Column(name = "google_user_id", nullable = false, length = 100)
    private long google_id;

    @Column(name = "profile_url", length = 2048)
    private String profileUrl;


    @OneToMany(
            mappedBy = "user",
            cascade = CascadeType.ALL,
            orphanRemoval = true
    )
    private List<ConnectedAccount> connectedAccounts = new ArrayList<>();
}
