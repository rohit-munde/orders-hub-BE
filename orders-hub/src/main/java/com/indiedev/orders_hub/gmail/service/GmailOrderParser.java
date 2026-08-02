package com.indiedev.orders_hub.gmail.service;

import com.indiedev.orders_hub.gmail.dto.GmailMessageContent;
import com.indiedev.orders_hub.gmail.dto.GmailOrderPreview;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@Component
public class GmailOrderParser {

    private static final Pattern SENDER_NAME = Pattern.compile("^\\s*\\\"?([^\\\"<]+?)\\\"?\\s*<[^>]+>\\s*$");
    private static final Pattern SENDER_DOMAIN = Pattern.compile("@([A-Za-z0-9.-]+)");
    private static final Pattern ORDER_NUMBER = Pattern.compile(
            "(?i)\\border\\s*(?:(?:number|no\\.?|id)\\s*[:#-]?\\s*|[#:]\\s*)([a-z0-9][a-z0-9-]{2,})\\b"
    );
    private static final Pattern BILL_AMOUNT = Pattern.compile(
            "(?i)\\b(?:grand\\s+total|order\\s+total|total\\s+amount|amount\\s+paid|bill\\s+amount|total)"
                    + "\\s*:?\\s*(?:INR|USD|Rs\\.?)?\\s*[₹$]?\\s*([0-9][0-9,]*(?:\\.[0-9]{1,2})?)"
    );
    private static final Pattern OTP = Pattern.compile(
            "(?i)\\b(?:delivery\\s+)?otp\\s*(?:is|:|-)?\\s*(\\d{4,8})\\b"
    );
    private static final Pattern UNPAID = Pattern.compile(
            "(?i)\\bunpaid\\b|\\bpayment\\s+(?:failed|pending|declined)\\b"
    );
    private static final Pattern PAID = Pattern.compile(
            "(?i)\\bpaid\\b|\\bpayment\\s+(?:successful|received|completed)\\b"
    );

    public GmailOrderPreview parse(GmailMessageContent message) {
        String body = valueOrEmpty(message.body());
        String searchableText = valueOrEmpty(message.subject()) + "\n" + body;

        return new GmailOrderPreview(
                message.gmailMessageId(),
                brandName(message.from()),
                extract(ORDER_NUMBER, body),
                amount(body),
                paymentState(searchableText),
                extract(OTP, body),
                status(searchableText),
                List.of()
        );
    }

    private String brandName(String sender) {
        if (sender == null || sender.isBlank()) {
            return null;
        }

        Matcher name = SENDER_NAME.matcher(sender);
        if (name.matches()) {
            return name.group(1).trim();
        }

        Matcher domain = SENDER_DOMAIN.matcher(sender);
        return domain.find() ? domain.group(1).toLowerCase() : sender.trim();
    }

    private BigDecimal amount(String body) {
        String value = extract(BILL_AMOUNT, body);
        return value == null ? null : new BigDecimal(value.replace(",", ""));
    }

    private Boolean paymentState(String text) {
        if (UNPAID.matcher(text).find()) {
            return false;
        }
        return PAID.matcher(text).find() ? true : null;
    }

    private String status(String text) {
        if (contains(text, "cancelled", "canceled")) {
            return "CANCELLED";
        }
        if (contains(text, "out for delivery")) {
            return "OUT_FOR_DELIVERY";
        }
        if (contains(text, "delivered")) {
            return "DELIVERED";
        }
        if (contains(text, "shipped")) {
            return "SHIPPED";
        }
        if (contains(text, "dispatched")) {
            return "DISPATCHED";
        }
        if (contains(text, "confirmed", "order placed")) {
            return "CONFIRMED";
        }
        return "UNKNOWN";
    }

    private boolean contains(String text, String... values) {
        String lowercaseText = text.toLowerCase();
        for (String value : values) {
            if (lowercaseText.contains(value)) {
                return true;
            }
        }
        return false;
    }

    private String extract(Pattern pattern, String value) {
        Matcher matcher = pattern.matcher(value);
        return matcher.find() ? matcher.group(1).trim() : null;
    }

    private String valueOrEmpty(String value) {
        return value == null ? "" : value;
    }
}
