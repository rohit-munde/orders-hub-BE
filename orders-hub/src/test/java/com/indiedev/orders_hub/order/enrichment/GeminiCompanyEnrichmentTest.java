package com.indiedev.orders_hub.order.enrichment;

import com.indiedev.orders_hub.order.entity.Company;
import org.junit.jupiter.api.Test;

import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertTrue;

class GeminiCompanyEnrichmentTest {

    @Test
    void returnsEmptyWhenApiKeyMissing() {
        GeminiCompanyEnrichment client = new GeminiCompanyEnrichment(null);
        Optional<CompanyEnrichmentCandidate> result = client.enrichCompany(new Company());
        assertTrue(result.isEmpty());
    }
}
