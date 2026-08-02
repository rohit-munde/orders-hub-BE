# Gmail Order Import Design

## Goal

Find a configurable batch of likely order emails, parse them, and safely create or enrich OrdersHub orders without creating duplicate orders or processing the same Gmail message repeatedly.

## Scope

This version will:

- run during the existing initial Google/Gmail connection flow;
- search one configurable Gmail batch, with a default of 25 and a hard maximum of 50;
- keep Gmail filtering behind a replaceable `OrderEmailCandidateFinder` interface;
- fetch and parse each unprocessed candidate independently;
- import only candidates with both a reliable order number and merchant identity;
- create or non-destructively update one order per user, merchant, and order number;
- record every imported, ignored, or failed Gmail message in `order_email_sources`;
- continue processing other candidates when one candidate fails; and
- return import counts and saved order previews in the existing authentication response.

This version will not add Gmail pagination/history sync, background jobs, manual reprocessing endpoints, shipment entities, raw email storage, attachment parsing, AI classification, or OAuth token refresh during later syncs.

## Architecture

The pipeline has five focused responsibilities:

1. `GmailOrderEmailCandidateFinder` owns the Gmail query and returns candidate Gmail IDs. It implements `OrderEmailCandidateFinder`, allowing later filtering changes without changing parsing or persistence.
2. `GmailApiClient` searches up to the requested batch size and fetches full message bodies.
3. `GmailOrderParser` converts a decoded email to `GmailOrderPreview`, including a normalized merchant key and currency when available.
4. `GmailOrderImportService` validates, deduplicates, and atomically saves an order together with its source-message record.
5. `GmailSyncService` coordinates the batch, keeps external Gmail calls outside database transactions, isolates per-message failures, and reports counts.

## Data Model

### `orders`

The existing table gains:

- `merchant_key`: normalized sender domain used as stable merchant identity;
- `currency`: nullable ISO-style currency code such as `INR` or `USD`;
- nullable `paid` semantics so unknown payment state is not treated as unpaid; and
- a unique constraint on `(user_id, merchant_key, order_no)`.

`status` uses a constrained enum: `UNKNOWN`, `CONFIRMED`, `DISPATCHED`, `SHIPPED`, `OUT_FOR_DELIVERY`, `DELIVERED`, or `CANCELLED`.

`merchant_key` remains nullable at the schema level during the legacy transition. Every new Gmail-imported order has a merchant key. When exactly one older order has the same user/order number and no merchant key, the first matching import backfills it; ambiguous legacy rows are left unchanged rather than guessed. A later repository-wide migration can enforce non-null after legacy data has been audited.

### `order_email_sources`

The new table contains:

- `id`;
- `connected_account_id`;
- `gmail_message_id`;
- nullable `order_id`;
- `processing_status`: `IMPORTED`, `IGNORED`, or `FAILED`;
- nullable safe `failure_reason`;
- `parser_version`;
- `processed_at`; and
- inherited creation/update timestamps.

It has a unique constraint on `(connected_account_id, gmail_message_id)`. It never stores the raw subject, sender, email body, access token, or extracted OTP.

## Candidate Validation

A parsed candidate is persistable only when:

- `gmailMessageId` is present;
- `merchantKey` is present; and
- `orderNo` is present.

An invalid candidate is recorded as `IGNORED` without creating an order. A previously ignored message is eligible for processing again only after the parser version increases. A failed message remains retryable. An imported message is always skipped on later syncs.

## Order Identity and Merge Rules

An order is identified by `(userId, merchantKey, normalizedOrderNo)`. This prevents order-number collisions across merchants while allowing confirmation, shipment, and delivery emails to enrich the same order.

Updates are non-destructive:

- null or blank incoming values never erase stored values;
- brand, amount, currency, and items fill missing data rather than replacing known data;
- `paid=true` can advance null or false, while false only fills an unknown value;
- status advances by lifecycle precedence and never regresses because an older email is processed later;
- `CANCELLED` is terminal; `DELIVERED` cannot regress to an earlier delivery state and does not override `CANCELLED`; and
- OTP is not persisted.

Order items are saved only when the parser returns items and the existing order has none. The current parser returns no items, so item extraction remains a later improvement.

## Batch and Failure Behavior

The finder requests one Gmail page and caps the configured batch size between 1 and 50. IDs are processed in Gmail response order.

Each message is handled independently:

- already imported or same-version ignored messages increment `skippedCount` without fetching the full body;
- valid parsed candidates increment `savedCount` after atomic order/source persistence;
- invalid candidates increment `ignoredCount`;
- fetch, parse, or persistence failures record a safe failure where possible and increment `failedCount`; and
- one failure does not discard successful imports from the same batch.

A failure of the Gmail search itself fails the sync through the existing safe Google API exception handling.

Order/source writes acquire a database write lock on the OrdersHub user row. This serializes concurrent imports for the same user, including first inserts, so a stale message cannot overwrite a newer lifecycle state or downgrade an already imported source.

## Response Contract

`syncPreview` becomes a batch result:

```json
{
  "query": "newer_than:1y {...}",
  "candidateCount": 3,
  "savedCount": 2,
  "skippedCount": 0,
  "ignoredCount": 1,
  "failedCount": 0,
  "orders": [
    {
      "gmailMessageId": "18abc123",
      "merchantKey": "amazon.in",
      "brandName": "Amazon",
      "orderNo": "ORDER-123",
      "billAmount": 1499.00,
      "currency": "INR",
      "paid": true,
      "otp": null,
      "status": "SHIPPED",
      "orderItems": []
    }
  ]
}
```

The preview may still show an extracted OTP for the immediate response, but persistence must not copy it into the order table.

## Schema Management

The repository currently manages its schema through Hibernate `ddl-auto=update` and has no migration framework or baseline migrations. This change will follow that existing mechanism. Introducing Flyway or Liquibase is a separate repository-wide migration project rather than being partially introduced for one table.

## Testing

- Finder tests verify the exact query, configured batch limit, hard cap, and unsafe configuration rejection.
- Gmail client tests verify multiple IDs are returned without exposing page tokens.
- Parser tests verify normalized merchant identity and currency extraction.
- Import-service tests verify create, update, ignored, retry, non-destructive merging, status precedence, and source linkage.
- Sync tests verify mixed saved/skipped/ignored/failed batches continue independently.
- Connection/auth tests verify the batch result is returned without exposing Google tokens or raw email content.
- The complete Maven suite must pass before the implementation is considered complete.
