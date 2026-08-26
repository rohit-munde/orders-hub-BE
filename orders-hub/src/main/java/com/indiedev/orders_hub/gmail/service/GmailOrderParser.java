package com.indiedev.orders_hub.gmail.service;

import com.indiedev.orders_hub.gmail.dto.GmailMessageContent;
import com.indiedev.orders_hub.gmail.dto.GmailOrderPreview;
import com.indiedev.orders_hub.order.entity.OrderStatus;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.util.ArrayList;
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
            "(?i)\\b(?:grand\\s+total|order\\s+total|total\\s+amount|amount\\s+paid|bill\\s+amount|total\\s+refund|refund\\s+total|refund\\s+subtotal|subtotal|total)"
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
    private static final Pattern DELIVERED = Pattern.compile(
            "(?i)\\b(?:has\\s+been|was|is|package\\s+)?delivered\\b(?!\\s+by)"
    );
    private static final int PARSER_VERSION = 8;

    public GmailOrderPreview parse(GmailMessageContent message) {
        return parseAll(message).getFirst();
    }

    public List<GmailOrderPreview> parseAll(GmailMessageContent message) {
        String body = valueOrEmpty(message.body());
        String searchableText = valueOrEmpty(message.subject()) + "\n" + body;
        List<OrderMatch> orderMatches = orderMatches(body);

        if (orderMatches.isEmpty()) {
            return List.of(preview(message, null, body, searchableText));
        }

        List<GmailOrderPreview> previews = new ArrayList<>();
        for (int i = 0; i < orderMatches.size(); i++) {
            OrderMatch orderMatch = orderMatches.get(i);
            int sectionEnd = i + 1 < orderMatches.size() ? orderMatches.get(i + 1).start() : body.length();
            String orderSection = body.substring(orderMatch.start(), sectionEnd);
            previews.add(preview(message, orderMatch.orderNo(), orderSection, searchableText));
        }
        return List.copyOf(previews);
    }

    private GmailOrderPreview preview(
            GmailMessageContent message,
            String orderNo,
            String amountText,
            String searchableText
    ) {
        Amount amount = amount(amountText, senderDomain(message.from()));

        return new GmailOrderPreview(
                message.gmailMessageId(),
                brandName(message.from()),
                orderNo,
                amount.value(),
                amount.currency(),
                paymentState(searchableText),
                extract(OTP, searchableText),
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

    private String senderDomain(String sender) {
        if (sender == null) {
            return null;
        }
        Matcher domain = SENDER_DOMAIN.matcher(sender);
        return domain.find() ? domain.group(1).toLowerCase(Locale.ROOT) : null;
    }

    private Amount amount(String body, String senderDomain) {
        Matcher matcher = BILL_AMOUNT.matcher(body);
        if (!matcher.find()) {
            return new Amount(null, null);
        }
        String marker = matcher.group(1);
        String currency = marker == null ? inferredCurrency(senderDomain) : switch (marker.toUpperCase(Locale.ROOT)) {
            case "USD", "$" -> "USD";
            default -> "INR";
        };
        return new Amount(
                new BigDecimal(matcher.group(2).replace(",", "")),
                currency
        );
    }

    private String inferredCurrency(String domain) {
        return domain != null && domain.endsWith(".in") ? "INR" : null;
    }

    private Boolean paymentState(String text) {
        if (UNPAID.matcher(text).find()) {
            return false;
        }
        return PAID.matcher(text).find() ? true : null;
    }

    private OrderStatus status(String text) {
        if (contains(text, "refund", "refunded")) {
            return OrderStatus.REFUNDED;
        }
        if (contains(text, "cancelled", "canceled")) {
            return OrderStatus.CANCELLED;
        }
        if (DELIVERED.matcher(text).find()) {
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
        if (contains(text, "confirmed", "order placed", "order has been placed")) {
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

    private List<OrderMatch> orderMatches(String body) {
        Matcher matcher = ORDER_NUMBER.matcher(body);
        List<OrderMatch> matches = new ArrayList<>();
        while (matcher.find()) {
            String orderNo = matcher.group(1).trim();
            if (hasDigit(orderNo)) {
                matches.add(new OrderMatch(matcher.start(), orderNo));
            }
        }
        return matches;
    }

    private boolean hasDigit(String value) {
        for (int i = 0; i < value.length(); i++) {
            if (Character.isDigit(value.charAt(i))) {
                return true;
            }
        }
        return false;
    }

    private String valueOrEmpty(String value) {
        return value == null ? "" : value;
    }

    private record Amount(BigDecimal value, String currency) {
    }

    private record OrderMatch(int start, String orderNo) {
    }
}
