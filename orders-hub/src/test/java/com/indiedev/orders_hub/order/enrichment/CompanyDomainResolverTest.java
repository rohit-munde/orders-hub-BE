package com.indiedev.orders_hub.order.enrichment;

import com.indiedev.orders_hub.order.validation.CompanyLogoValidator;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.web.client.RestClient;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class CompanyDomainResolverTest {

    private CompanyDomainResolver resolver;

    @BeforeEach
    void setUp() {
        resolver = new CompanyDomainResolver(RestClient.builder());
    }

    @Test
    void extractsDomainFromBrandNameWhenExtensionIsPresent() {
        assertEquals("coinsstuff.com", resolver.extractDomainFromBrandName("coinsstuff.com"));
        assertEquals("amazon.in", resolver.extractDomainFromBrandName("https://www.amazon.in"));
        assertNull(resolver.extractDomainFromBrandName("Sockscarving®"));
    }

    @Test
    void generatesCandidateDomainsForNicheBrands() {
        List<String> candidates = resolver.generateCandidateDomains("Sockscarving®");
        assertTrue(candidates.contains("sockscarving.com"));
        assertTrue(candidates.contains("sockscarving.in"));

        List<String> milldCandidates = resolver.generateCandidateDomains("Milld Store");
        assertTrue(milldCandidates.contains("milld.com") || milldCandidates.contains("milldstore.com"));

        List<String> gauripriyaCandidates = resolver.generateCandidateDomains("Gauripriya ecom");
        assertTrue(gauripriyaCandidates.contains("gauripriya.com") || gauripriyaCandidates.contains("gauripriyaecom.com"));
    }

    @Test
    void resolvesUsableLogoFromMultiProviderList() {
        CompanyLogoValidator validator = mock(CompanyLogoValidator.class);
        when(validator.isUsableLogoUrl("https://unavatar.io/amazon.in")).thenReturn(true);

        String logoUrl = resolver.resolveLogoForDomain("amazon.in", validator);
        assertEquals("https://unavatar.io/amazon.in", logoUrl);
    }
}
