# Orders Sync and List Design

## Goal

Let the authenticated mobile app refresh Gmail orders without another Google login and display the user's most recently placed orders first.

## Scope

This change will:

- limit every Gmail order search to a rolling maximum of 45 days;
- keep the existing initial sync during Google login;
- add an authenticated endpoint for later order syncs;
- refresh expired Google access tokens from the encrypted refresh token;
- persist an estimated order-placement timestamp;
- add an authenticated orders-list endpoint sorted newest placement first; and
- keep the current idempotency, concurrency, and privacy protections.

It will not add a background scheduler, push notifications, Gmail history sync, pagination, or merchant-specific order-date parsing.

## API Contract

### Trigger sync

```http
POST /api/v1/orders/sync?force=false
Authorization: Bearer <ordershub-jwt>
```

The mobile app calls this when the Orders screen opens. The backend reads `userId` from the verified application JWT and never accepts a user ID or Google credential from the request.

- `force=false` skips Gmail when `lastSyncAt` is less than 15 minutes old.
- Pull-to-refresh calls the same endpoint with `force=true`.
- A successful or cooldown response returns HTTP 200. Outcome is `COMPLETED` or `COOLDOWN`; cooldown counts are zero.
- A user without a connected Gmail account receives HTTP 409 with `Gmail account is not connected`.
- Google failures remain safe HTTP 502 responses and mark the connected account sync as failed.

### List orders

```http
GET /api/v1/orders
Authorization: Bearer <ordershub-jwt>
```

The response contains `lastSyncedAt` and the user's orders sorted by:

1. `placedAt DESC`;
2. stable order ID descending as a tie-breaker; and
3. null `placedAt` values last for legacy rows.

Order DTOs expose only application data: ID, merchant/brand, order number, amount, currency, payment state, status, placement time, and items. They never expose OTPs, Gmail message IDs, raw email content, or Google tokens.

## Gmail Window

Replace the free-form `newerThan` setting with integer `lookbackDays`:

- default: 45;
- valid range: 1 through 45;
- values outside the range fail fast instead of silently widening the search; and
- the finder generates `newer_than:<lookbackDays>d`.

The message batch default and hard maximum are both 50. This imports the latest useful subset without adding potentially slow Gmail pagination to a synchronous mobile request.

## Placement Time

Gmail `messages.get` returns `internalDate`, the timestamp Gmail uses for inbox ordering. The client converts it to `emailReceivedAt` and carries it through the parsed candidate.

For a new order, `placedAt` starts as the candidate email time. Later emails for the same order may only move `placedAt` earlier. Therefore, when confirmation, shipment, and delivery messages are all present, the earliest known order email becomes the placement estimate regardless of processing order.

This is intentionally an estimate. If only a shipment or delivery email is available, its time is the best safe fallback. Merchant-specific dates inside email bodies can improve this later without changing the database or API contract.

## Sync Flow

1. Resolve the current user from the verified application JWT.
2. Load that user's Google connected account.
3. If the cooldown applies, return without calling Google.
4. Reuse the encrypted access token when it remains valid for at least another 60 seconds.
5. Otherwise decrypt the refresh token, exchange it for a new access token, encrypt the new token, and update its expiry. Preserve the existing refresh token and scopes when Google omits them.
6. Run the existing Gmail candidate, parse, and import pipeline.
7. Mark sync success/failure and return only safe counts.
8. The mobile app calls `GET /api/v1/orders` to render the refreshed list.

External Google requests remain outside database transactions. Existing user-level write locking continues to serialize order/source merges.

## Testing

- Finder tests prove default 45 days and reject 0 or more than 45.
- Gmail client tests prove `internalDate` conversion.
- Import tests prove placement time keeps the earliest candidate timestamp.
- OAuth tests prove refresh-token exchange and omission-safe token persistence.
- Sync endpoint tests prove JWT ownership, 15-minute cooldown, forced refresh, missing account, success, and safe failure.
- Orders endpoint repository/controller tests prove user isolation, newest-first ordering, nulls last, stable ties, and DTO privacy.
- The complete Maven suite must pass before merge.
