package com.indiedev.orders_hub.order.enrichment;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.indiedev.orders_hub.order.entity.Company;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;
import org.springframework.web.client.RestClient;

import java.net.URI;
import java.util.List;
import java.util.Map;
import java.util.Optional;

@Slf4j
@Component
@RequiredArgsConstructor
public class GeminiCompanyEnrichment implements CompanyEnrichmentClient {

    private final RestClient.Builder restClientBuilder;
    private final ObjectMapper objectMapper = new ObjectMapper();

    @Value("${gemini.api.key:}")
    private String apiKey;

    @Value("${gemini.api.model:gemini-1.5-flash}")
    private String modelName;

    @Override
    public Optional<CompanyEnrichmentCandidate> enrichCompany(Company company) {
        if (!StringUtils.hasText(apiKey)) {
            log.warn("Gemini API key is missing. Skipping LLM enrichment.");
            return Optional.empty();
        }

        try {
            String prompt = buildPrompt(company);
            String url = "https://generativelanguage.googleapis.com/v1beta/models/" + modelName + ":generateContent?key=" + apiKey;

            Map<String, Object> requestBody = Map.of(
                    "contents", List.of(
                            Map.of("parts", List.of(Map.of("text", prompt)))
                    ),
                    "generationConfig", Map.of(
                            "responseMimeType", "application/json"
                    )
            );

            String responseJson = restClientBuilder.build()
                    .post()
                    .uri(URI.create(url))
                    .contentType(MediaType.APPLICATION_JSON)
                    .body(requestBody)
                    .retrieve()
                    .body(String.class);

            if (!StringUtils.hasText(responseJson)) {
                return Optional.empty();
            }

            return parseGeminiResponse(responseJson);

        } catch (Exception e) {
            log.error("Failed to enrich company {} via Gemini API", company.getBrandName(), e);
            return Optional.empty();
        }
    }

    private String buildPrompt(Company company) {
        return """
                You are a master data enrichment assistant for e-commerce companies.
                Identify and normalize official details for the company with raw brand name: "%s".

                Output strict JSON adhering to this schema:
                {
                  "canonicalBrandName": "Clean, official brand name (e.g., 'Instamart', 'Amazon')",
                  "primaryDomainName": "Clean primary domain without protocol or paths (e.g., 'swiggy.com', 'amazon.in')",
                  "logoUrl": "Direct high quality square/icon logo URL if known, or null",
                  "domains": [
                     { "domainName": "domain.com", "primaryDomain": true }
                  ],
                  "confidenceScore": 0.95,
                  "reason": "Brief explanation of how domain was matched"
                }

                Rules:
                1. For sub-brands or services like 'Instamart', set canonicalBrandName to 'Instamart' and primaryDomainName to their parent domain 'swiggy.com'.
                2. For niche, D2C, or small e-commerce brands, infer their likely primary domain name (e.g., gauripriyaecom.com, milldstore.com, sockscarving.com).
                3. Do not include protocols (http/https), www, or trailing slashes in primaryDomainName or domainName.
                4. Return confidenceScore between 0.00 and 1.00. Set confidence >= 0.85 when domain is confident.
                5. If logoUrl is unknown or placeholder, return null.
                """.formatted(company.getBrandName());
    }

    private Optional<CompanyEnrichmentCandidate> parseGeminiResponse(String json) {
        try {
            var rootNode = objectMapper.readTree(json);
            var candidatesNode = rootNode.path("candidates");
            if (candidatesNode.isEmpty()) {
                return Optional.empty();
            }

            var textNode = candidatesNode.get(0)
                    .path("content")
                    .path("parts")
                    .get(0)
                    .path("text");

            if (textNode.isMissingNode()) {
                return Optional.empty();
            }

            String contentText = textNode.asText();
            CompanyEnrichmentCandidate candidate = objectMapper.readValue(contentText, CompanyEnrichmentCandidate.class);
            return Optional.ofNullable(candidate);

        } catch (Exception e) {
            log.error("Failed to parse Gemini JSON response", e);
            return Optional.empty();
        }
    }
}
