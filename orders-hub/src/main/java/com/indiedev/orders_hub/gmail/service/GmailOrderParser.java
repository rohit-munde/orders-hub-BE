package com.indiedev.orders_hub.gmail.service;

import com.indiedev.orders_hub.gmail.dto.GmailMessageContent;
import com.indiedev.orders_hub.gmail.dto.GmailOrderPreview;
import com.indiedev.orders_hub.order.OrderStatus;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.util.List;
import java.util.Locale;
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
                    + "\\s*:?\\s*(?:(INR|USD|Rs\\.?|₹|\\$)\\s*)?([0-9][0-9,]*(?:\\.[0-9]{1,2})?)"
    );
    private static final Pattern OTP = Pattern.compile(
            "(?i)\\b(?:delivery\\s+)?otp\\s*(?:is|:|-)?\\s*(\\d{4,8})\\b"
    );
    private static final Pattern UNPAID = Pattern.compile(
            "(?i)\\b(?:unpaid|not\\s+paid)\\b|\\bpayment\\s+(?:failed|pending|declined)\\b"
    );
    private static final Pattern PAID = Pattern.compile(
            "(?i)\\bpaid\\b|\\bpayment\\s+(?:successful|received|completed)\\b"
    );
    private static final int PARSER_VERSION = 2;

    public GmailOrderPreview parse(GmailMessageContent message) {
        String body = valueOrEmpty(message.body());
        String searchableText = valueOrEmpty(message.subject()) + "\n" + body;
        Amount amount = amount(body);

        return new GmailOrderPreview(
                message.gmailMessageId(),
                merchantKey(message.from()),
                brandName(message.from()),
                extract(ORDER_NUMBER, body),
                amount.value(),
                amount.currency(),
                paymentState(searchableText),
                extract(OTP, body),
                status(searchableText),
                message.receivedAt(),
                List.of()
        );
    }

    public int version() {
        return PARSER_VERSION;
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
        return domain.find() ? domain.group(1).toLowerCase(Locale.ROOT) : sender.trim();
    }

    private String merchantKey(String sender) {
        if (sender == null) {
            return null;
        }
        Matcher domain = SENDER_DOMAIN.matcher(sender);
        return domain.find() ? domain.group(1).toLowerCase(Locale.ROOT) : null;
    }

    private Amount amount(String body) {
        Matcher matcher = BILL_AMOUNT.matcher(body);
        if (!matcher.find()) {
            return new Amount(null, null);
        }
        String marker = matcher.group(1);
        String currency = marker == null ? null : switch (marker.toUpperCase()) {
            case "USD", "$" -> "USD";
            default -> "INR";
        };
        return new Amount(
                new BigDecimal(matcher.group(2).replace(",", "")),
                currency
        );
    }

    private Boolean paymentState(String text) {
        if (UNPAID.matcher(text).find()) {
            return false;
        }
        return PAID.matcher(text).find() ? true : null;
    }

    private OrderStatus status(String text) {
        if (contains(text, "cancelled", "canceled")) {
            return OrderStatus.CANCELLED;
        }
        if (contains(text, "delivered")) {
            return OrderStatus.DELIVERED;
        }
        if (contains(text, "out for delivery")) {
            return OrderStatus.OUT_FOR_DELIVERY;
        }
        if (contains(text, "shipped")) {
            return OrderStatus.SHIPPED;
        }
        if (contains(text, "dispatched")) {
            return OrderStatus.DISPATCHED;
        }
        if (contains(text, "confirmed", "order placed")) {
            return OrderStatus.CONFIRMED;
        }
        return OrderStatus.UNKNOWN;
    }

    private boolean contains(String text, String... values) {
        String lowercaseText = text.toLowerCase(Locale.ROOT);
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

    private record Amount(BigDecimal value, String currency) {
    }
}
