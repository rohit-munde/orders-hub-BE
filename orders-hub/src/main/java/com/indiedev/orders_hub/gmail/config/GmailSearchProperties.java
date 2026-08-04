package com.indiedev.orders_hub.gmail.config;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;
import org.springframework.validation.annotation.Validated;

import java.util.List;

@Component
@ConfigurationProperties(prefix = "ordershub.gmail.search")
@Validated
@Getter
@Setter
public class GmailSearchProperties {

    @Min(1)
    @Max(45)
    private int lookbackDays = 45;

    @Min(1)
    @Max(50)
    private int batchSize = 50;
    private List<String> subjectKeywords = List.of(
            "order", "ordered", "shipped", "delivered", "dispatched", "invoice", "receipt"
    );
    private List<String> senderDomains = List.of(
            "amazon.in", "flipkart.com", "myntra.com", "meesho.com"
    );
}
