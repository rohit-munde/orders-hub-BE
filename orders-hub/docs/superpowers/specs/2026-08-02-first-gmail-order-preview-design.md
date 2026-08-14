# First Gmail Order Preview Design

## Goal

After Google login finds Gmail messages matching the existing order-email query, fetch the first matching message and return a best-effort order-shaped JSON preview.

## Scope

This first version will:

- search Gmail with the existing configured query;
- request at most one matching Gmail message;
- call `users.messages.get` with `format=full` for that message;
- decode nested Base64URL MIME body data;
- prefer `text/plain` and fall back to readable text derived from `text/html`;
- extract basic order fields with deterministic patterns; and
- include the result in the existing Google-login response.

This version will not add a separate endpoint, persist an `Order`, refresh an expired token, call an AI service, download attachments, or add merchant-specific parsers.

## Response Contract

The existing `syncPreview` object will contain the search query, the selected Gmail message ID, and an `orderPreview`:

```json
{
  "query": "newer_than:1y {...}",
  "gmailMessageId": "18abc123",
  "orderPreview": {
    "gmailMessageId": "18abc123",
    "brandName": "Amazon",
    "orderNo": "ORDER-123",
    "billAmount": 1499.00,
    "paid": true,
    "otp": null,
    "status": "SHIPPED",
    "orderItems": []
  }
}
```

When the search has no result, `gmailMessageId` and `orderPreview` will both be `null`.

## Components and Data Flow

1. `GmailSyncService` builds the current search query and requests one message ID.
2. `GmailApiClient` fetches that message using `format=full`, reads relevant headers, recursively finds inline text MIME parts, and decodes Base64URL data into text.
3. `GmailOrderParser` converts the decoded message into `GmailOrderPreview` using small, explicit patterns.
4. `GmailSyncService` returns the query, Gmail message ID, and parsed preview through the existing authentication response.

The parsed preview is a DTO and is deliberately separate from the JPA `Order` entity.

## Basic Extraction Rules

- `brandName`: sender display name, falling back to the sender domain.
- `orderNo`: value following common labels such as `Order`, `Order number`, `Order no`, or `Order ID`.
- `billAmount`: numeric value following common total/amount labels and an optional currency marker.
- `paid`: `true` for explicit paid/payment-successful text, `false` for explicit unpaid or payment-failed text, otherwise `null`.
- `otp`: four-to-eight digit value next to an OTP label.
- `status`: one of `DELIVERED`, `OUT_FOR_DELIVERY`, `SHIPPED`, `DISPATCHED`, `CANCELLED`, `CONFIRMED`, or `UNKNOWN`, based on explicit text.
- `orderItems`: an empty list in this first version.

Missing optional values are returned as `null`. Email bodies and extracted private content must not be logged.

## Error Handling

Existing Google API exception handling remains in use for failed Gmail requests or empty full-message responses. Malformed or missing body data produces an empty body and therefore a partial preview rather than failing the complete login.

## Testing

- Verify the Gmail client sends `format=full` and decodes a nested multipart Base64URL body.
- Verify HTML fallback decoding.
- Verify representative order number, amount, payment, OTP, status, and sender extraction.
- Verify sync uses only the first Gmail ID.
- Verify an empty search returns a null preview without requesting a message.
- Run the complete Maven test suite.
