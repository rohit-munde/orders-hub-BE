package com.indiedev.orders_hub.order.enrichment;

public record CompanyDomainCandidate(
        String domainName,
        boolean primaryDomain
) {
}
