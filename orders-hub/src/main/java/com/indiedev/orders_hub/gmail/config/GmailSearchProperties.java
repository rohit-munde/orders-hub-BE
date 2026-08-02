package com.indiedev.orders_hub.gmail.config;

import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

import java.util.List;

@Component
@ConfigurationProperties(prefix = "ordershub.gmail.search")
@Getter
@Setter
public class GmailSearchProperties {

    private String newerThan = "1y";
    private int batchSize = 25;
    private List<String> subjectKeywords = List.of(
            "order", "ordered", "shipped", "delivered", "dispatched", "invoice", "receipt"
    );
    private List<String> senderDomains = List.of(
            "amazon.in", "flipkart.com", "myntra.com", "meesho.com"
    );
}
