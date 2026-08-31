package com.indiedev.orders_hub.order.entity;

import com.indiedev.orders_hub.common.BaseEntity;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
@Entity
@Table(name = "company_domains",
uniqueConstraints = {@UniqueConstraint(
        name = "uk_company_domain",
        columnNames = {"domain_name"}
)
}
)
public class CompanyDomain extends BaseEntity {

    @Id
    @GeneratedValue(strategy = jakarta.persistence.GenerationType.IDENTITY)
    private long id;

    @Column(name = "domain_name", nullable = false, length = 255)
    private String domainName;

    @Column(name = "is_primary", nullable = false)
    private boolean primaryDomain;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "company_id", nullable = false, foreignKey = @ForeignKey(name = "fk_company_domains_company"))
    private Company company;
}
