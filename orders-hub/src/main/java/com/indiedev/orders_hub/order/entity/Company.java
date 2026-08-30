package com.indiedev.orders_hub.order.entity;

import com.indiedev.orders_hub.common.BaseEntity;
import com.indiedev.orders_hub.order.enums.EnrichmentStatusEnum;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

import java.util.HashSet;
import java.util.Set;

@Getter
@Setter
@Entity
@Table(name = "company")
public class Company extends BaseEntity {
    @Id
    @GeneratedValue(strategy = jakarta.persistence.GenerationType.IDENTITY)
    private long id;

    @Column
    private String brandName;

    @Column(name = "logo_url", length = 2048)
    private String logoUrl;

    @Column(name = "primary_domain_name", length = 1024)
    private String primaryDomainName;

    @OneToMany(mappedBy = "company", cascade = CascadeType.ALL, orphanRemoval = true)
    private Set<CompanyDomain> companyDomains = new HashSet<>();

    @Enumerated(EnumType.STRING)
    @Column(name = "enrichment_status", nullable = false, length = 32)
    private EnrichmentStatusEnum enrichmentStatus = EnrichmentStatusEnum.PENDING;
}
