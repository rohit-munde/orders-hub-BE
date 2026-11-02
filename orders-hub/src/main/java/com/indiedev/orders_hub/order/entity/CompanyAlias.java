package com.indiedev.orders_hub.order.entity;

import com.indiedev.orders_hub.common.BaseEntity;
import com.indiedev.orders_hub.order.enums.CompanyAliasType;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
@Entity
@Table(
        name = "company_aliases",
        indexes = {
                @Index(name = "idx_company_aliases_normalized_alias", columnList = "normalized_alias_value", unique = true),
                @Index(name = "idx_company_aliases_company_id", columnList = "company_id")
        }
)
public class CompanyAlias extends BaseEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "company_id", nullable = false)
    private Company company;

    @Enumerated(EnumType.STRING)
    @Column(name = "alias_type", nullable = false, length = 50)
    private CompanyAliasType aliasType;

    @Column(name = "alias_value", nullable = false, length = 255)
    private String aliasValue;

    @Column(name = "normalized_alias_value", nullable = false, length = 255, unique = true)
    private String normalizedAliasValue;
}
