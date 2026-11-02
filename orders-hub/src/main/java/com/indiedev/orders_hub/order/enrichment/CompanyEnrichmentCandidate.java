package com.indiedev.orders_hub.order.enrichment;

import java.math.BigDecimal;
import java.util.List;

public record CompanyEnrichmentCandidate(
        String canonicalBrandName,
        String primaryDomainName,
        String logoUrl,
        List<CompanyDomainCandidate> domains,
        BigDecimal confidenceScore,
        String reason
) {
}
