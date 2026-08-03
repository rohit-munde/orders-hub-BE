# Orders Sync and Newest-First List Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use `superpowers:subagent-driven-development` (recommended) or `superpowers:executing-plans` to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Let an authenticated mobile user refresh Gmail orders without logging in again and fetch a privacy-safe order list sorted by estimated placement time, newest first.

**Architecture:** Keep Gmail discovery behind the existing `OrderEmailCandidateFinder`, carry Gmail `internalDate` through parsing into a nullable `Order.placedAt`, and preserve the earliest message time when multiple emails describe one order. A small access-token service reuses a still-valid encrypted token or refreshes it outside a database transaction. An order sync service owns cooldown and sync status, while an order query service maps entities to explicit API DTOs.

**Tech Stack:** Java 21, Spring Boot 4.1, Spring MVC/Security, Spring Data JPA, MySQL, H2, JUnit 5, Mockito, Maven.

## Global Constraints

- Gmail searches use a rolling `newer_than:45d` maximum and request at most 50 message IDs.
- Filtering remains isolated in `OrderEmailCandidateFinder` so merchant rules can change later.
- API identity comes only from the signed application JWT `userId` claim.
- A normal screen-open sync observes a 15-minute cooldown; `force=true` bypasses it.
- Google tokens remain encrypted at rest and are never returned or logged.
- Gmail network calls and OAuth refresh calls run outside database transactions.
- Existing order/source unique constraints and per-user import locking remain in place.
- API DTOs never expose OTP, Gmail message IDs, raw email content, or Google tokens.
- No scheduler, push sync, Gmail history API, pagination, or merchant-specific order-date parsing is added in this change.

---

### Task 1: Enforce the rolling 45-day Gmail search window

**Files:**
- Modify: `src/main/java/com/indiedev/orders_hub/gmail/config/GmailSearchProperties.java`
- Modify: `src/main/java/com/indiedev/orders_hub/gmail/service/GmailOrderEmailCandidateFinder.java`
- Modify: `src/main/resources/application.properties`
- Modify: `src/test/java/com/indiedev/orders_hub/gmail/service/GmailOrderEmailCandidateFinderTest.java`

**Interface changes:**
- Replace `String newerThan` with `int lookbackDays`, default `45`.
- Set `batchSize` default to `50` and retain an absolute maximum of `50`.
- Build `newer_than:<lookbackDays>d` inside the finder.

- [ ] **Step 1: Write failing finder tests**

Update the default-query test and add boundary tests proving `1` and `45` days are accepted while `0` and `46` are rejected before Gmail is called.

```java
String expectedQuery = "newer_than:45d {subject:order subject:receipt from:amazon.in}";
verify(client).findMessageIds("access-token", expectedQuery, 50);

properties.setLookbackDays(46);
assertThrows(IllegalArgumentException.class, () -> finder.find("access-token"));
verifyNoInteractions(client);
```

- [ ] **Step 2: Run the focused test and confirm the expected failure**

Run: `./mvnw -Dtest=GmailOrderEmailCandidateFinderTest test`

Expected: compilation/assertion failures because `lookbackDays` does not exist and defaults are still `1y`/`25`.

- [ ] **Step 3: Implement the minimal configuration change**

Use validation annotations on the configuration properties for startup-time validation, retain a direct finder guard for unit-created properties, and remove the free-form duration regex. Keep existing keyword/domain validation unchanged.

```java
@Min(1)
@Max(45)
private int lookbackDays = 45;

@Min(1)
@Max(50)
private int batchSize = 50;
```

Change the environment property to:

```properties
ordershub.gmail.search.lookback-days=${GMAIL_SEARCH_LOOKBACK_DAYS:45}
ordershub.gmail.search.batch-size=${GMAIL_SEARCH_BATCH_SIZE:50}
```

- [ ] **Step 4: Verify the focused test passes**

Run: `./mvnw -Dtest=GmailOrderEmailCandidateFinderTest test`

Expected: all finder tests pass, including the hard 45-day and 50-message limits.

- [ ] **Step 5: Commit Task 1**

```bash
git add src/main/java/com/indiedev/orders_hub/gmail/config/GmailSearchProperties.java src/main/java/com/indiedev/orders_hub/gmail/service/GmailOrderEmailCandidateFinder.java src/main/resources/application.properties src/test/java/com/indiedev/orders_hub/gmail/service/GmailOrderEmailCandidateFinderTest.java
git commit -m "feat: cap Gmail order searches at 45 days"
```

### Task 2: Persist the best available order placement timestamp

**Files:**
- Modify: `src/main/java/com/indiedev/orders_hub/gmail/client/GmailApiClient.java`
- Modify: `src/main/java/com/indiedev/orders_hub/gmail/dto/GmailMessageContent.java`
- Modify: `src/main/java/com/indiedev/orders_hub/gmail/dto/GmailOrderPreview.java`
- Modify: `src/main/java/com/indiedev/orders_hub/gmail/service/GmailOrderParser.java`
- Modify: `src/main/java/com/indiedev/orders_hub/order/Order.java`
- Modify: `src/main/java/com/indiedev/orders_hub/order/service/GmailOrderImportService.java`
- Modify: `src/test/java/com/indiedev/orders_hub/gmail/client/GmailApiClientTest.java`
- Modify: `src/test/java/com/indiedev/orders_hub/gmail/service/GmailOrderParserTest.java`
- Modify: `src/test/java/com/indiedev/orders_hub/gmail/service/GmailSyncServiceTest.java`
- Modify: `src/test/java/com/indiedev/orders_hub/order/service/GmailOrderImportServiceTest.java`
- Modify: `src/test/java/com/indiedev/orders_hub/order/service/GmailOrderImportConcurrencyTest.java`

**Interface changes:**
- `GmailMessageContent` carries `Instant receivedAt` from Gmail `internalDate`.
- `GmailOrderPreview` carries `Instant placedAt` as the current estimate.
- `Order` gains nullable column `placed_at`.
- Parser version advances from `1` to `2`.

- [ ] **Step 1: Write failing Gmail client and parser tests**

Add `"internalDate":"1785688200000"` to a full-message fixture and assert it becomes the exact `Instant`. Assert the parser passes that instant unchanged into the order preview. Add a malformed/missing `internalDate` case that produces `null` instead of failing the whole message.

```java
assertEquals(Instant.ofEpochMilli(1785688200000L), message.receivedAt());
assertEquals(message.receivedAt(), parser.parse(message).placedAt());
```

- [ ] **Step 2: Run the client/parser tests and confirm they fail**

Run: `./mvnw -Dtest=GmailApiClientTest,GmailOrderParserTest test`

Expected: compilation failures because neither DTO exposes the timestamp.

- [ ] **Step 3: Parse Gmail `internalDate` safely**

Add `String internalDate` to the private Gmail message response record, convert epoch milliseconds to `Instant`, and return `null` for absent or invalid values. Do not infer placement time from headers or body text in this phase.

- [ ] **Step 4: Write failing import merge tests**

Cover all three rules: a new order receives the candidate time, a later email cannot move it forward, and an earlier email moves it backward.

```java
assertEquals(earlier, existing.getPlacedAt());
```

Also change the parser-version test to prove a source imported with version `1` is processed once by parser version `2`; this is the safe backfill path for orders already imported before `placed_at` existed.

- [ ] **Step 5: Run the import tests and confirm they fail**

Run: `./mvnw -Dtest=GmailOrderImportServiceTest test`

Expected: compilation/assertion failures because `Order.placedAt` and timestamp merge behavior are missing.

- [ ] **Step 6: Implement timestamp persistence and one-time reprocessing**

Add the nullable `Instant placedAt` field. During merge, set it when absent or when the new candidate timestamp is earlier. Change retry logic so any source with an older parser version can be processed once, while same-version imported/ignored sources remain skipped and failed sources remain retryable.

```java
if (candidate.placedAt() != null
        && (order.getPlacedAt() == null || candidate.placedAt().isBefore(order.getPlacedAt()))) {
    order.setPlacedAt(candidate.placedAt());
}
```

Update every literal `GmailMessageContent`/`GmailOrderPreview` test fixture to include the new timestamp argument and return parser version `2`.

- [ ] **Step 7: Verify timestamp and import tests pass**

Run: `./mvnw -Dtest=GmailApiClientTest,GmailOrderParserTest,GmailOrderImportServiceTest,GmailOrderImportConcurrencyTest,GmailSyncServiceTest test`

Expected: all timestamp, merge, concurrency, and sync tests pass.

- [ ] **Step 8: Commit Task 2**

```bash
git add src/main/java/com/indiedev/orders_hub/gmail src/main/java/com/indiedev/orders_hub/order src/test/java/com/indiedev/orders_hub/gmail src/test/java/com/indiedev/orders_hub/order
git commit -m "feat: track estimated order placement time"
```

### Task 3: Reuse or refresh the connected Gmail access token

**Files:**
- Create: `src/main/java/com/indiedev/orders_hub/config/TimeConfig.java`
- Create: `src/main/java/com/indiedev/orders_hub/gmail/exception/GmailConnectionRequiredException.java`
- Create: `src/main/java/com/indiedev/orders_hub/gmail/service/GoogleAccessTokenService.java`
- Modify: `src/main/java/com/indiedev/orders_hub/gmail/service/GoogleOAuthService.java`
- Modify: `src/main/java/com/indiedev/orders_hub/connectedaccount/ConnectedAccountPersistenceService.java`
- Modify: `src/test/java/com/indiedev/orders_hub/gmail/service/GoogleOAuthServiceTest.java`
- Create: `src/test/java/com/indiedev/orders_hub/gmail/service/GoogleAccessTokenServiceTest.java`
- Modify: `src/test/java/com/indiedev/orders_hub/connectedaccount/ConnectedAccountPersistenceServiceTest.java`

**Interface changes:**
- `GoogleOAuthService.refreshAccessToken(String refreshToken)` exchanges a refresh token for a short-lived access token.
- `GoogleAccessTokenService.getValidAccessToken(ConnectedAccount account)` applies a 60-second safety margin.
- `ConnectedAccountPersistenceService.storeRefreshedAccessToken(...)` encrypts and persists the replacement token and expiry.

- [ ] **Step 1: Write a failing OAuth refresh test**

Assert the form includes only the refresh grant inputs and that a response without `refresh_token`, `id_token`, or `scope` is valid.

```java
expectedForm.add("grant_type", "refresh_token");
expectedForm.add("refresh_token", "stored-refresh-token");
expectedForm.add("client_id", "web-client-id");
expectedForm.add("client_secret", "web-client-secret");
```

- [ ] **Step 2: Run the OAuth test and confirm it fails**

Run: `./mvnw -Dtest=GoogleOAuthServiceTest test`

Expected: compilation failure because the refresh API and refresh response type do not exist.

- [ ] **Step 3: Implement the refresh exchange**

Share the token endpoint and safe HTTP exception handling, but keep authorization-code validation separate because only code exchange requires an ID token. Return a small `RefreshedToken(accessToken, expiresIn, scope)` record.

- [ ] **Step 4: Write failing access-token selection tests**

Use `Clock.fixed(...)` and verify:

- a token valid for more than 60 seconds is decrypted and reused without OAuth;
- an expired or nearly expired token decrypts the refresh token, calls OAuth, and persists the encrypted replacement;
- missing refresh credentials produce a safe reconnect-required exception without leaking ciphertext or token text;
- a refresh response with no scope preserves existing granted scopes.

- [ ] **Step 5: Run the token tests and confirm they fail**

Run: `./mvnw -Dtest=GoogleAccessTokenServiceTest,ConnectedAccountPersistenceServiceTest test`

Expected: compilation failures because token selection and refresh persistence are missing.

- [ ] **Step 6: Implement token selection and persistence**

Provide one UTC `Clock` bean. Keep decryption and OAuth refresh in the non-transactional token service; make only the database update transactional. Preserve the existing encrypted refresh token and existing scopes when Google omits replacement values.

- [ ] **Step 7: Verify token tests pass**

Run: `./mvnw -Dtest=GoogleOAuthServiceTest,GoogleAccessTokenServiceTest,ConnectedAccountPersistenceServiceTest test`

Expected: all OAuth, encryption-boundary, expiry-margin, and preservation tests pass.

- [ ] **Step 8: Commit Task 3**

```bash
git add src/main/java/com/indiedev/orders_hub/config/TimeConfig.java src/main/java/com/indiedev/orders_hub/gmail/exception/GmailConnectionRequiredException.java src/main/java/com/indiedev/orders_hub/gmail/service src/main/java/com/indiedev/orders_hub/connectedaccount/ConnectedAccountPersistenceService.java src/test/java/com/indiedev/orders_hub/gmail/service src/test/java/com/indiedev/orders_hub/connectedaccount/ConnectedAccountPersistenceServiceTest.java
git commit -m "feat: refresh connected Gmail access tokens"
```

### Task 4: Add cooldown-aware authenticated order sync

**Files:**
- Modify: `src/main/java/com/indiedev/orders_hub/connectedaccount/ConnectedAccountRepository.java`
- Modify: `src/main/java/com/indiedev/orders_hub/connectedaccount/ConnectedAccountPersistenceService.java`
- Create: `src/main/java/com/indiedev/orders_hub/order/dto/OrderSyncResponse.java`
- Create: `src/main/java/com/indiedev/orders_hub/order/service/OrderSyncService.java`
- Modify: `src/main/java/com/indiedev/orders_hub/common/ApiExceptionHandler.java`
- Create: `src/test/java/com/indiedev/orders_hub/order/service/OrderSyncServiceTest.java`
- Modify: `src/test/java/com/indiedev/orders_hub/connectedaccount/ConnectedAccountPersistenceServiceTest.java`

**Interface changes:**
- Find the current Google account by authenticated user ID and provider, fetching its user association.
- `OrderSyncService.sync(long userId, boolean force)` returns only outcome, timestamps, and counts.
- Add persistence methods for `SYNCING`, `SYNCED`, and `FAILED` transitions.

- [ ] **Step 1: Write failing sync orchestration tests**

Use a fixed clock and repository/service doubles to prove:

- no connected Gmail account raises `GmailConnectionRequiredException`;
- `force=false` inside 15 minutes returns `COOLDOWN`, zero counts, and makes no token/Gmail call;
- `force=true` bypasses cooldown;
- a completed sync marks `SYNCING` before the external work and `SYNCED` afterward;
- OAuth/Gmail failures mark `FAILED` and are rethrown for the existing HTTP 502 mapping.

```java
OrderSyncResponse response = service.sync(7, false);
assertEquals(OrderSyncResponse.Outcome.COOLDOWN, response.outcome());
verifyNoInteractions(accessTokenService, gmailSyncService);
```

- [ ] **Step 2: Run the sync-service test and confirm it fails**

Run: `./mvnw -Dtest=OrderSyncServiceTest test`

Expected: compilation failure because the authenticated sync service and response do not exist.

- [ ] **Step 3: Implement the sync flow**

Use a `Duration.ofMinutes(15)` constant. Resolve the account from `userId`, check cooldown before changing state, mark the account syncing, obtain a valid token, run the existing `GmailSyncService`, and then mark success with the fixed current time. Return counts from `GmailSyncPreview` but never return its query or parsed email previews.

```java
public record OrderSyncResponse(
        Outcome outcome,
        Instant lastSyncedAt,
        int candidateCount,
        int savedCount,
        int skippedCount,
        int ignoredCount,
        int failedCount
) {}
```

- [ ] **Step 4: Map reconnect-required failures to HTTP 409**

Add one exception-handler branch. Missing account uses the exact safe message `Gmail account is not connected`; missing/invalid stored refresh credentials use a safe reconnect message. Existing `GoogleApiException` handling remains HTTP 502.

- [ ] **Step 5: Verify sync-service and persistence tests pass**

Run: `./mvnw -Dtest=OrderSyncServiceTest,ConnectedAccountPersistenceServiceTest test`

Expected: cooldown, force, success, failure, and status transition tests pass.

- [ ] **Step 6: Commit Task 4**

```bash
git add src/main/java/com/indiedev/orders_hub/connectedaccount src/main/java/com/indiedev/orders_hub/gmail/exception src/main/java/com/indiedev/orders_hub/order/dto/OrderSyncResponse.java src/main/java/com/indiedev/orders_hub/order/service/OrderSyncService.java src/main/java/com/indiedev/orders_hub/common/ApiExceptionHandler.java src/test/java/com/indiedev/orders_hub/order/service/OrderSyncServiceTest.java src/test/java/com/indiedev/orders_hub/connectedaccount/ConnectedAccountPersistenceServiceTest.java
git commit -m "feat: add cooldown-aware Gmail order sync"
```

### Task 5: Expose sync and newest-first order-list endpoints

**Files:**
- Modify: `src/main/java/com/indiedev/orders_hub/order/OrderRepository.java`
- Create: `src/main/java/com/indiedev/orders_hub/order/dto/OrderListResponse.java`
- Create: `src/main/java/com/indiedev/orders_hub/order/service/OrderQueryService.java`
- Create: `src/main/java/com/indiedev/orders_hub/order/controller/OrderController.java`
- Create: `src/test/java/com/indiedev/orders_hub/order/OrderRepositoryOrderingTest.java`
- Create: `src/test/java/com/indiedev/orders_hub/order/service/OrderQueryServiceTest.java`
- Create: `src/test/java/com/indiedev/orders_hub/order/controller/OrderControllerTest.java`

**HTTP API:**
- `POST /api/v1/orders/sync?force=false`
- `GET /api/v1/orders`

- [ ] **Step 1: Write a failing repository ordering test**

Persist orders for two users with newer, older, tied, and null `placedAt` values, including an order with multiple items. Assert the target user's result order is:

1. `placedAt DESC`;
2. `id DESC` for equal timestamps;
3. null timestamps last;
4. no orders belonging to the other user;
5. one result per order with items already loaded.

- [ ] **Step 2: Run the repository test and confirm it fails**

Run: `./mvnw -Dtest=OrderRepositoryOrderingTest test`

Expected: compilation failure because the ordered user query does not exist.

- [ ] **Step 3: Implement the ordered repository query**

Use an explicit JPQL null-ordering expression and an entity graph for `orderItems`, avoiding an N+1 query while keeping mapping inside a read-only transaction.

```java
order by case when orders.placedAt is null then 1 else 0 end,
         orders.placedAt desc,
         orders.id desc
```

- [ ] **Step 4: Write failing query-service privacy tests**

Assert the list response includes `lastSyncedAt`, ID, merchant/display brand, order number, amount, currency, paid state, status, `placedAt`, and item details. Assert the DTO record components contain none of `otp`, `gmailMessageId`, `body`, `accessToken`, or `refreshToken`.

- [ ] **Step 5: Implement explicit list DTO mapping**

Create immutable nested `OrderResponse` and `OrderItemResponse` records. Map within `@Transactional(readOnly = true)` and return `List.copyOf(...)`. If no connected account exists, return existing user orders with `lastSyncedAt = null`.

- [ ] **Step 6: Write failing controller and security tests**

Use a focused MVC slice with the real `SecurityConfig` and mocked order services. Prove anonymous GET/POST requests receive HTTP 401. Build a JWT with `userId=7`, call each endpoint, and verify only that ID is passed to the services. Verify `force` defaults to false and is forwarded when true. Add a missing-claim test with the safe error message.

```java
Jwt jwt = Jwt.withTokenValue("app-token")
        .header("alg", "none")
        .claim("userId", 7L)
        .build();
```

- [ ] **Step 7: Implement the controller**

Use `@AuthenticationPrincipal Jwt`, derive a `long` from the numeric `userId` claim, and accept no user ID, Gmail account, or Google credential in request input. Rely on the existing `.anyRequest().authenticated()` security rule.

- [ ] **Step 8: Verify endpoint-focused tests pass**

Run: `./mvnw -Dtest=OrderRepositoryOrderingTest,OrderQueryServiceTest,OrderControllerTest test`

Expected: newest-first ordering, DTO privacy, anonymous rejection, JWT identity, and force forwarding tests pass.

- [ ] **Step 9: Commit Task 5**

```bash
git add src/main/java/com/indiedev/orders_hub/order src/test/java/com/indiedev/orders_hub/order
git commit -m "feat: expose order sync and list APIs"
```

### Task 6: Complete regression and quality verification

**Files:**
- Modify only files required by test failures or review findings.

- [ ] **Step 1: Run all tests**

Run: `./mvnw test`

Expected: Maven reports `BUILD SUCCESS` with zero failures and zero errors.

- [ ] **Step 2: Run static repository checks**

Run: `git diff --check`

Expected: no whitespace errors.

Run: `rg -n "newer-than|GMAIL_SEARCH_NEWER_THAN|1y|batch-size.*25" src README.md docs --glob '!docs/superpowers/**'`

Expected: no stale production configuration for the old one-year/25-message behavior.

Run: `rg -n "otp|gmailMessageId|accessToken|refreshToken|body" src/main/java/com/indiedev/orders_hub/order/dto src/main/java/com/indiedev/orders_hub/order/controller`

Expected: no private Gmail content or token fields in public order API DTOs/controllers.

- [ ] **Step 3: Review transaction and logging boundaries**

Confirm OAuth/Gmail calls are made only from non-transactional services, transactional persistence methods contain no network calls, and logs contain only account IDs/counts.

- [ ] **Step 4: Review schema compatibility**

Confirm `orders.placed_at` is nullable so existing rows remain valid under the project's current `spring.jpa.hibernate.ddl-auto=update` setup.

- [ ] **Step 5: Commit any verification fixes**

If verification required edits:

```bash
git add src/main src/test
git commit -m "fix: harden order sync API"
```

If no edits were required, do not create an empty commit.

- [ ] **Step 6: Prepare merge handoff**

Report the exact tests run, final commit list, branch/upstream difference, and any remaining operational note. Do not push until explicitly requested.
