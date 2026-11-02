package com.indiedev.orders_hub.order.service;

import com.indiedev.orders_hub.order.enrichment.CompanyDomainCandidate;
import com.indiedev.orders_hub.order.enrichment.CompanyDomainResolver;
import com.indiedev.orders_hub.order.enrichment.CompanyEnrichmentCandidate;
import com.indiedev.orders_hub.order.enrichment.CompanyEnrichmentClient;
import com.indiedev.orders_hub.order.entity.Company;
import com.indiedev.orders_hub.order.entity.CompanyAlias;
import com.indiedev.orders_hub.order.entity.CompanyDomain;
import com.indiedev.orders_hub.order.enums.CompanyAliasType;
import com.indiedev.orders_hub.order.enums.EnrichmentStatusEnum;
import com.indiedev.orders_hub.order.repository.CompanyAliasRepository;
import com.indiedev.orders_hub.order.repository.CompanyMasterRepository;
import com.indiedev.orders_hub.order.util.CompanyNormalizationUtil;
import com.indiedev.orders_hub.order.validation.CompanyLogoValidator;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.util.StringUtils;

import java.math.BigDecimal;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

@Slf4j
@Service
@RequiredArgsConstructor
public class CompanyEnrichmentService {

    private final CompanyMasterRepository companyMasterRepository;
    private final CompanyEnrichmentClient companyEnrichmentClient;
    private final TransactionTemplate transactionTemplate;
    private final CompanyAliasRepository companyAliasRepository;
    private final CompanyLogoValidator companyLogoValidator;
    private final CompanyDomainResolver companyDomainResolver;

    @Value("${company.enrichment.min-confidence:0.85}")
    private BigDecimal minConfidence;

    public void enrich(long companyId) {
        Company company = markEnriching(companyId);

        CompanyEnrichmentCandidate candidate = companyEnrichmentClient.enrichCompany(company)
                .orElse(null);

        if (candidate == null || !isUsable(candidate)) {
            candidate = attemptFallbackResolution(company, candidate);
        }

        if (candidate == null || !isUsable(candidate)) {
            updateStatus(companyId, EnrichmentStatusEnum.FAILED);
            return;
        }

        applyVerifiedCandidate(companyId, candidate);
    }

    private CompanyEnrichmentCandidate attemptFallbackResolution(Company company, CompanyEnrichmentCandidate existingCandidate) {
        String brandName = company.getBrandName();
        if (!StringUtils.hasText(brandName)) {
            return existingCandidate;
        }

        List<String> candidateDomains = companyDomainResolver.generateCandidateDomains(brandName);
        String searchDomain = companyDomainResolver.resolveDomainViaSearch(brandName);
        if (StringUtils.hasText(searchDomain) && !candidateDomains.contains(searchDomain)) {
            candidateDomains.add(0, searchDomain);
        }

        String resolvedDomain = null;
        String resolvedLogoUrl = null;

        for (String domain : candidateDomains) {
            String logoUrl = companyDomainResolver.resolveLogoForDomain(domain, companyLogoValidator);
            if (StringUtils.hasText(logoUrl)) {
                resolvedDomain = domain;
                resolvedLogoUrl = logoUrl;
                break;
            }
        }

        if (!StringUtils.hasText(resolvedDomain) && StringUtils.hasText(searchDomain)) {
            resolvedDomain = searchDomain;
        }

        if (!StringUtils.hasText(resolvedDomain) && !candidateDomains.isEmpty()) {
            resolvedDomain = candidateDomains.getFirst();
        }

        if (StringUtils.hasText(resolvedDomain)) {
            String cleanBrandName = CompanyNormalizationUtil.toHumanBrandName(brandName);
            if (!StringUtils.hasText(cleanBrandName)) {
                cleanBrandName = brandName.replaceAll("[®™©]", "").strip();
            }
            return new CompanyEnrichmentCandidate(
                    cleanBrandName,
                    resolvedDomain,
                    resolvedLogoUrl,
                    List.of(new CompanyDomainCandidate(resolvedDomain, true)),
                    BigDecimal.valueOf(0.90),
                    "Resolved via domain heuristic & fallback search"
            );
        }

        return existingCandidate;
    }

    private Company markEnriching(long companyId) {
        return transactionTemplate.execute(status -> {
            Company company = companyMasterRepository.findById(companyId)
                    .orElseThrow(() -> new RuntimeException("Company not found with id: " + companyId));

            if (company.getEnrichmentStatus() == null) {
                throw new RuntimeException("Enrichment status not found for company with id: " + companyId);
            }

            company.setEnrichmentStatus(EnrichmentStatusEnum.ENRICHING);
            return companyMasterRepository.save(company);
        });
    }

    private void updateStatus(long companyId, EnrichmentStatusEnum statusEnum) {
        transactionTemplate.executeWithoutResult(status -> {
            Company company = companyMasterRepository.findById(companyId)
                    .orElseThrow(() -> new RuntimeException("Company not found with id: " + companyId));
            company.setEnrichmentStatus(statusEnum);
            company.setUpdatedAt(java.time.LocalDateTime.now());
            companyMasterRepository.save(company);
        });
    }

    private void applyVerifiedCandidate(long companyId, CompanyEnrichmentCandidate candidate) {
        transactionTemplate.executeWithoutResult(status -> {
            Company company = companyMasterRepository.findById(companyId)
                    .orElseThrow(() -> new RuntimeException("Company not found with id: " + companyId));
            applyCandidate(company, candidate);
            company.setEnrichmentStatus(EnrichmentStatusEnum.VERIFIED);
            companyMasterRepository.save(company);
        });
    }

    private boolean isUsable(CompanyEnrichmentCandidate candidate) {
        if (candidate == null) {
            return false;
        }

        if (candidate.confidenceScore() == null || candidate.confidenceScore().compareTo(minConfidence) < 0) {
            return false;
        }

        return StringUtils.hasText(candidate.canonicalBrandName())
                || StringUtils.hasText(candidate.primaryDomainName())
                || StringUtils.hasText(candidate.logoUrl())
                || (candidate.domains() != null && candidate.domains().stream()
                .anyMatch(domain -> StringUtils.hasText(domain.domainName())));
    }

    private void applyCandidate(Company company, CompanyEnrichmentCandidate candidate) {
        applyCanonicalBrandName(company, candidate);

        String humanBrand = CompanyNormalizationUtil.toHumanBrandName(company.getBrandName());
        if (StringUtils.hasText(humanBrand) && !companyMasterRepository.findByBrandNameIgnoreCase(humanBrand)
                .filter(existing -> existing.getId() != company.getId()).isPresent()) {
            company.setBrandName(humanBrand);
        }

        if (StringUtils.hasText(candidate.primaryDomainName())) {
            String primaryDomainName = CompanyNormalizationUtil.normalizeAliasValue(candidate.primaryDomainName());
            if (StringUtils.hasText(primaryDomainName)) {
                company.setPrimaryDomainName(primaryDomainName);
                addAliasIfAvailable(company, CompanyAliasType.DOMAIN, primaryDomainName);
            }
        }

        String targetLogoUrl = candidate.logoUrl();
        if (StringUtils.hasText(targetLogoUrl) && companyLogoValidator.isUsableLogoUrl(targetLogoUrl)) {
            company.setLogoUrl(targetLogoUrl.strip());
        } else if (StringUtils.hasText(company.getPrimaryDomainName())) {
            String logoUrl = companyDomainResolver.resolveLogoForDomain(company.getPrimaryDomainName(), companyLogoValidator);
            if (StringUtils.hasText(logoUrl)) {
                company.setLogoUrl(logoUrl);
            }
        }

        if (candidate.domains() != null) {
            Set<String> domainNames = new HashSet<>();
            company.getCompanyDomains().forEach(domain -> domainNames.add(domain.getDomainName()));

            candidate.domains().stream()
                    .filter(domain -> StringUtils.hasText(domain.domainName()))
                    .forEach(domainCandidate -> {
                        String normalizedDomain = CompanyNormalizationUtil.normalizeAliasValue(domainCandidate.domainName());
                        if (StringUtils.hasText(normalizedDomain) && domainNames.add(normalizedDomain)) {
                            company.getCompanyDomains().add(companyDomain(company, domainCandidate, normalizedDomain));
                            addAliasIfAvailable(company, CompanyAliasType.DOMAIN, normalizedDomain);
                        }
                    });
        }
    }

    private void applyCanonicalBrandName(Company company, CompanyEnrichmentCandidate candidate) {
        if (!StringUtils.hasText(candidate.canonicalBrandName())) {
            return;
        }

        String rawCanonical = candidate.canonicalBrandName().strip();
        String canonicalBrandName = CompanyNormalizationUtil.toHumanBrandName(rawCanonical);
        if (!StringUtils.hasText(canonicalBrandName)) {
            canonicalBrandName = rawCanonical;
        }

        boolean usedByAnotherCompany = companyMasterRepository.findByBrandNameIgnoreCase(canonicalBrandName)
                .filter(existing -> existing.getId() != company.getId())
                .isPresent();

        if (!usedByAnotherCompany) {
            company.setBrandName(canonicalBrandName);
        }
        addAliasIfAvailable(company, CompanyAliasType.CANONICAL_NAME, canonicalBrandName);
    }

    private void addAliasIfAvailable(Company company, CompanyAliasType aliasType, String aliasValue) {
        String normalizedAliasValue = CompanyNormalizationUtil.normalizeAliasValue(aliasValue);
        if (!StringUtils.hasText(normalizedAliasValue)) {
            return;
        }

        boolean alreadyOnCompany = company.getCompanyAliases().stream()
                .anyMatch(alias -> alias.getNormalizedAliasValue().equals(normalizedAliasValue));
        if (alreadyOnCompany) {
            return;
        }

        boolean usedByAnotherCompany = companyAliasRepository.findByNormalizedAliasValue(normalizedAliasValue)
                .filter(alias -> alias.getCompany().getId() != company.getId())
                .isPresent();
        if (usedByAnotherCompany) {
            return;
        }

        CompanyAlias alias = new CompanyAlias();
        alias.setCompany(company);
        alias.setAliasType(aliasType);
        alias.setAliasValue(aliasValue.strip());
        alias.setNormalizedAliasValue(normalizedAliasValue);
        company.getCompanyAliases().add(alias);
    }

    private CompanyDomain companyDomain(Company company, CompanyDomainCandidate candidate, String normalizedDomain) {
        CompanyDomain companyDomain = new CompanyDomain();
        companyDomain.setCompany(company);
        companyDomain.setDomainName(normalizedDomain);
        companyDomain.setPrimaryDomain(candidate.primaryDomain());
        return companyDomain;
    }
}
