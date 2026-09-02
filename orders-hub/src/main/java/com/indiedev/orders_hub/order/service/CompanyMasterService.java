package com.indiedev.orders_hub.order.service;

import com.indiedev.orders_hub.order.entity.Company;
import com.indiedev.orders_hub.order.repository.CompanyAliasRepository;
import com.indiedev.orders_hub.order.repository.CompanyDomainRepository;
import com.indiedev.orders_hub.order.repository.CompanyMasterRepository;
import com.indiedev.orders_hub.order.util.CompanyNormalizationUtil;
import lombok.RequiredArgsConstructor;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.util.Optional;

@Service
@RequiredArgsConstructor
public class CompanyMasterService {

    private final CompanyMasterRepository companyMasterRepository;
    private final CompanyDomainRepository companyDomainRepository;
    private final CompanyAliasRepository companyAliasRepository;

    @Transactional
    public FindOrCreateCompanyResult findOrCreateByBrandName(String brandName) {
        if (!StringUtils.hasText(brandName)) {
            return new FindOrCreateCompanyResult(null, false);
        }

        String rawBrandName = brandName.strip();
        String humanBrandName = CompanyNormalizationUtil.toHumanBrandName(rawBrandName);
        String normalizedAlias = CompanyNormalizationUtil.normalizeAliasValue(rawBrandName);

        // 1. Search by clean human brand name (e.g. "Amazon")
        Optional<Company> existingCompany = companyMasterRepository.findByBrandNameIgnoreCase(humanBrandName);

        // 2. Search by raw brand name (e.g. "Amazon.in")
        if (existingCompany.isEmpty()) {
            existingCompany = companyMasterRepository.findByBrandNameIgnoreCase(rawBrandName);
        }

        // 3. Search by domain repository (e.g. "amazon.in")
        if (existingCompany.isEmpty() && StringUtils.hasText(normalizedAlias)) {
            existingCompany = companyDomainRepository.findByDomainNameIgnoreCase(normalizedAlias)
                    .map(companyDomain -> companyDomain.getCompany());
        }

        // 4. Search by alias repository (e.g. "return@amazon.in" or "amazon.in")
        if (existingCompany.isEmpty() && StringUtils.hasText(normalizedAlias)) {
            existingCompany = companyAliasRepository.findByNormalizedAliasValue(normalizedAlias)
                    .map(companyAlias -> companyAlias.getCompany());
        }

        if (existingCompany.isPresent()) {
            return new FindOrCreateCompanyResult(existingCompany.get(), false);
        }

        String targetBrandName = StringUtils.hasText(humanBrandName) ? humanBrandName : rawBrandName;
        return createCompany(targetBrandName);
    }

    private FindOrCreateCompanyResult createCompany(String brandName) {
        Company company = new Company();
        company.setBrandName(brandName);
        try {
            return new FindOrCreateCompanyResult(companyMasterRepository.saveAndFlush(company), true);
        } catch (DataIntegrityViolationException ex) {
            return companyMasterRepository.findByBrandNameIgnoreCase(brandName)
                    .map(existing -> new FindOrCreateCompanyResult(existing, false))
                    .orElseThrow(() -> ex);
        }
    }

    public record FindOrCreateCompanyResult(Company company, boolean created) {
    }
}
