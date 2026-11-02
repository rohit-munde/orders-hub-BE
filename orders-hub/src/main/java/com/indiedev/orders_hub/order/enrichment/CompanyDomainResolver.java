package com.indiedev.orders_hub.order.enrichment;

import com.indiedev.orders_hub.order.util.CompanyNormalizationUtil;
import com.indiedev.orders_hub.order.validation.CompanyLogoValidator;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;
import org.springframework.web.client.RestClient;

import java.net.URI;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@Slf4j
@Component
@RequiredArgsConstructor
public class CompanyDomainResolver {

    private static final Pattern DOMAIN_PATTERN = Pattern.compile(
            "([a-zA-Z0-9-]+\\.(?:com|in|co\\.in|store|shop|org|net|io|co|biz|app|dev|me|xyz|online|site))"
    );

    private final RestClient.Builder restClientBuilder;

    public String extractDomainFromBrandName(String brandName) {
        if (!StringUtils.hasText(brandName)) {
            return null;
        }

        String normalized = CompanyNormalizationUtil.normalizeAliasValue(brandName);
        Matcher matcher = DOMAIN_PATTERN.matcher(normalized);
        if (matcher.find()) {
            return matcher.group(1).toLowerCase();
        }
        return null;
    }

    public List<String> generateCandidateDomains(String brandName) {
        List<String> candidates = new ArrayList<>();
        if (!StringUtils.hasText(brandName)) {
            return candidates;
        }

        String lower = brandName.toLowerCase();
        if (lower.contains("instamart")) {
            candidates.add("swiggy.com");
            candidates.add("swiggy.in");
        } else if (lower.contains("blinkit") || lower.contains("grofers")) {
            candidates.add("blinkit.com");
        } else if (lower.contains("zepto")) {
            candidates.add("zeptonow.com");
        }

        String extracted = extractDomainFromBrandName(brandName);
        if (StringUtils.hasText(extracted) && !candidates.contains(extracted)) {
            candidates.add(extracted);
        }

        String cleanName = brandName.replaceAll("[®™©]", "")
                .replaceAll("(?i)\\b(pvt|ltd|inc|llp|ecom|ecommerce|store|shop|online|official)\\b", "")
                .replaceAll("[^a-zA-Z0-9]", "")
                .toLowerCase()
                .strip();

        if (StringUtils.hasText(cleanName)) {
            candidates.add(cleanName + ".com");
            candidates.add(cleanName + ".in");
            candidates.add(cleanName + ".store");
            candidates.add(cleanName + ".shop");
            candidates.add(cleanName + ".co.in");
        }

        String fullCleanName = brandName.replaceAll("[®™©]", "")
                .replaceAll("[^a-zA-Z0-9]", "")
                .toLowerCase()
                .strip();

        if (StringUtils.hasText(fullCleanName) && !fullCleanName.equals(cleanName)) {
            candidates.add(fullCleanName + ".com");
            candidates.add(fullCleanName + ".in");
            candidates.add(fullCleanName + ".store");
        }

        return candidates;
    }

    public String resolveDomainViaSearch(String brandName) {
        if (!StringUtils.hasText(brandName)) {
            return null;
        }

        String extracted = extractDomainFromBrandName(brandName);
        if (StringUtils.hasText(extracted)) {
            return extracted;
        }

        try {
            String query = brandName.replaceAll("[®™©]", "").strip() + " official website";
            String searchUrl = "https://html.duckduckgo.com/html/?q=" + URI.create(query).toASCIIString();
            String html = restClientBuilder.build()
                    .get()
                    .uri(URI.create(searchUrl))
                    .header("User-Agent", "Mozilla/5.0 (Macintosh; Intel Mac OS X 10_15_7)")
                    .retrieve()
                    .body(String.class);

            if (StringUtils.hasText(html)) {
                Matcher matcher = DOMAIN_PATTERN.matcher(html.toLowerCase());
                while (matcher.find()) {
                    String foundDomain = matcher.group(1);
                    if (!foundDomain.contains("duckduckgo") && !foundDomain.contains("bing") && !foundDomain.contains("google")) {
                        return foundDomain;
                    }
                }
            }
        } catch (Exception e) {
            log.debug("Web search domain resolution failed for brand {}", brandName, e);
        }

        return null;
    }

    public String resolveLogoForDomain(String domain, CompanyLogoValidator validator) {
        if (!StringUtils.hasText(domain)) {
            return null;
        }

        String cleanDomain = CompanyNormalizationUtil.normalizeAliasValue(domain);
        if (!StringUtils.hasText(cleanDomain)) {
            return null;
        }

        List<String> logoProviderUrls = List.of(
                "https://www.google.com/s2/favicons?domain=" + cleanDomain + "&sz=128",
                "https://unavatar.io/" + cleanDomain,
                "https://cdn.brandfetch.io/" + cleanDomain
        );

        for (String logoUrl : logoProviderUrls) {
            if (validator.isUsableLogoUrl(logoUrl)) {
                return logoUrl;
            }
        }

        return null;
    }
}
