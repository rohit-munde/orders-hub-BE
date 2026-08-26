package com.indiedev.orders_hub.order.entity;

import com.indiedev.orders_hub.common.BaseEntity;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
@Entity
@Table(name = "company")
public class Company extends BaseEntity {
    @Id
    @GeneratedValue(strategy = jakarta.persistence.GenerationType.IDENTITY)
    private long id;

    @Column(unique = true, nullable = false, length = 255)
    private String brandName;

    @Column(name = "logo_url", length = 2048)
    private String logoUrl;

    @Column(name = "domain_name",unique = true, nullable = false, length = 1024)
    private String domainName;

}
