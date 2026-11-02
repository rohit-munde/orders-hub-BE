package com.indiedev.orders_hub.order.validation;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;
import org.springframework.web.client.RestClient;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.net.URI;

@Slf4j
@Component
@RequiredArgsConstructor
public class HttpCompanyLogoValidator implements CompanyLogoValidator {

    private final RestClient.Builder restClientBuilder;

    @Value("${company.enrichment.min-logo-width:16}")
    private int minLogoWidth;

    @Value("${company.enrichment.min-logo-height:16}")
    private int minLogoHeight;

    @Value("${company.enrichment.max-logo-width:1024}")
    private int maxLogoWidth;

    @Value("${company.enrichment.max-logo-height:1024}")
    private int maxLogoHeight;

    @Override
    public boolean isUsableLogoUrl(String logoUrl) {
        if (!StringUtils.hasText(logoUrl)) {
            return false;
        }

        String trimmedLogoUrl = logoUrl.strip();
        if (isRejectedUrl(trimmedLogoUrl)) {
            return false;
        }

        try {
            byte[] imageBytes = restClientBuilder.build()
                    .get()
                    .uri(URI.create(trimmedLogoUrl))
                    .retrieve()
                    .body(byte[].class);
            if (imageBytes == null || imageBytes.length == 0) {
                return false;
            }

            BufferedImage image = ImageIO.read(new ByteArrayInputStream(imageBytes));
            return image != null
                    && image.getWidth() >= minLogoWidth
                    && image.getWidth() <= maxLogoWidth
                    && image.getHeight() >= minLogoHeight
                    && image.getHeight() <= maxLogoHeight;
        } catch (Exception exception) {
            log.warn("Logo URL validation failed for {}", trimmedLogoUrl, exception);
            return false;
        }
    }

    private boolean isRejectedUrl(String logoUrl) {
        String normalizedLogoUrl = logoUrl.toLowerCase();
        return !(normalizedLogoUrl.startsWith("https://") || normalizedLogoUrl.startsWith("http://"))
                || normalizedLogoUrl.endsWith(".svg");
    }
}
