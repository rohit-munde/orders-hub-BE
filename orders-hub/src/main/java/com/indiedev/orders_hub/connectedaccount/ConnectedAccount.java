package com.indiedev.orders_hub.connectedaccount;

import com.indiedev.orders_hub.common.BaseEntity;
import com.indiedev.orders_hub.user.User;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

import java.time.Instant;

@Getter
@Setter
@Entity
@Table(
        name = "connected_accounts",
        uniqueConstraints = @UniqueConstraint(
                name = "uk_connected_account_provider_email",
                columnNames = {"provider", "email"}
        )
)
public class ConnectedAccount extends BaseEntity {

    @Id
    @GeneratedValue(strategy = jakarta.persistence.GenerationType.IDENTITY)
    private long id;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 32)
    private ConnectedAccountProvider provider;

    @Column(nullable = false, length = 255)
    private String email;

    @Lob
    @Column(name = "encrypted_refresh_token")
    private String encryptedRefreshToken;

    @Lob
    @Column(name = "encrypted_access_token")
    private String encryptedAccessToken;

    @Column(name = "access_token_expires_at")
    private Instant accessTokenExpiresAt;

    @Column(name = "granted_scopes", length = 2048)
    private String grantedScopes;

    @Enumerated(EnumType.STRING)
    @Column(name = "sync_status", nullable = false, length = 32)
    private ConnectedAccountSyncStatus syncStatus;

    @Column(name = "last_sync_at")
    private Instant lastSyncAt;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id", nullable = false, foreignKey = @ForeignKey(name = "fk_connected_account_user"))
    private User user;
}
