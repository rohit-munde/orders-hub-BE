# Gmail Order Import Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Import a configurable batch of Gmail order candidates into idempotent `orders` and `order_email_sources` records during initial Google connection.

**Architecture:** A replaceable finder owns Gmail filtering, the existing client fetches candidate content, the parser produces an immutable candidate, and a transactional import service owns validation and database merging. The sync coordinator performs network calls outside transactions and isolates failures per Gmail message.

**Tech Stack:** Java 21, Spring Boot 4.1, Spring Data JPA, MySQL, H2 for repository tests, JUnit 5, Mockito, Maven.

## Global Constraints

- Import only candidates with `gmailMessageId`, `merchantKey`, and `orderNo`.
- Never store raw email content, Google tokens, or extracted OTP values.
- Identify orders by `(user_id, merchant_key, order_no)` and source messages by `(connected_account_id, gmail_message_id)`.
- Merge non-destructively and never regress lifecycle status.
- Keep filtering replaceable and cap one search batch between 1 and 50 messages.
- Do not add background jobs, Gmail pagination/history, AI parsing, attachment parsing, or later-sync token refresh.

---

### Task 3: Persist order identity and Gmail source state

**Files:**
- Modify: `pom.xml`
- Modify: `src/main/java/com/indiedev/orders_hub/order/Order.java`
- Create: `src/main/java/com/indiedev/orders_hub/order/OrderRepository.java`
- Create: `src/main/java/com/indiedev/orders_hub/order/source/OrderEmailSource.java`
- Create: `src/main/java/com/indiedev/orders_hub/order/source/OrderEmailProcessingStatus.java`
- Create: `src/main/java/com/indiedev/orders_hub/order/source/OrderEmailSourceRepository.java`
- Create: `src/main/java/com/indiedev/orders_hub/order/service/GmailOrderImportService.java`
- Test: `src/test/java/com/indiedev/orders_hub/order/service/GmailOrderImportServiceTest.java`
- Test: `src/test/java/com/indiedev/orders_hub/order/source/OrderEmailSourceRepositoryTest.java`

**Interfaces:**
- Consumes: `ConnectedAccount` and `GmailOrderPreview`.
- Produces: `boolean shouldProcess(long accountId, String gmailMessageId, int parserVersion)`.
- Produces: `ImportResult importOrder(ConnectedAccount account, GmailOrderPreview candidate, int parserVersion)`.
- Produces: `void recordFailure(ConnectedAccount account, String gmailMessageId, int parserVersion)`.
- Produces: `record ImportResult(Outcome outcome, Order order)` where `Outcome` is `SAVED`, `IGNORED`, or `SKIPPED`.

- [ ] **Step 1: Write failing import-service tests**

Create literal candidates and repository doubles that prove a new order is saved with its imported source, a later message updates the same order without clearing known fields or persisting OTP, an invalid candidate becomes ignored, and imported/same-version ignored sources are skipped.

```java
ImportResult result = service.importOrder(account, candidate, 1);
assertEquals(Outcome.SAVED, result.outcome());
assertEquals("amazon.in", result.order().getMerchantKey());
assertNull(result.order().getOtp());
```

- [ ] **Step 2: Verify import tests fail for missing persistence types**

Run: `./mvnw -Dtest=GmailOrderImportServiceTest test`

Expected: compilation failure because the service, repositories, source entity, and order status types do not exist.

- [ ] **Step 3: Implement minimal transactional import behavior**

Add the order unique constraint and nullable payment/currency fields. Normalize merchant/order identities, fill only missing data, allow payment to advance to true, merge status by enum precedence, save order and source atomically, retry failed sources, and reprocess ignored sources only when the parser version increases.

- [ ] **Step 4: Verify import-service tests pass**

Run: `./mvnw -Dtest=GmailOrderImportServiceTest test`

Expected: all import service tests pass.

- [ ] **Step 5: Add H2 repository constraint tests**

Add the H2 test dependency and a `@DataJpaTest` that persists two source rows with the same connected account/message ID and asserts the second flush raises `DataIntegrityViolationException`. Also persist two orders with the same user/merchant/order number and assert the unique order identity is enforced.

- [ ] **Step 6: Verify repository constraint tests pass**

Run: `./mvnw -Dtest=OrderEmailSourceRepositoryTest test`

Expected: both database constraint tests pass using an in-memory H2 schema.

- [ ] **Step 7: Commit Task 1**

```bash
git add pom.xml src/main/java/com/indiedev/orders_hub/order src/test/java/com/indiedev/orders_hub/order
git commit -m "feat: add idempotent Gmail order persistence"
```

### Task 1: Make Gmail filtering replaceable and return a bounded batch

**Files:**
- Create: `src/main/java/com/indiedev/orders_hub/gmail/service/OrderEmailCandidateFinder.java`
- Create: `src/main/java/com/indiedev/orders_hub/gmail/service/GmailOrderEmailCandidateFinder.java`
- Modify: `src/main/java/com/indiedev/orders_hub/gmail/client/GmailApiClient.java`
- Modify: `src/main/java/com/indiedev/orders_hub/gmail/config/GmailSearchProperties.java`
- Modify: `src/main/resources/application.properties`
- Create: `src/test/java/com/indiedev/orders_hub/gmail/service/GmailOrderEmailCandidateFinderTest.java`
- Modify: `src/test/java/com/indiedev/orders_hub/gmail/client/GmailApiClientTest.java`

**Interfaces:**
- Produces: `CandidateBatch find(String accessToken)` and `record CandidateBatch(String query, List<String> gmailMessageIds)`.
- Produces: `List<String> findMessageIds(String accessToken, String query, int maxResults)`.

- [ ] **Step 1: Write failing finder/client batch tests**

Assert the finder builds the current literal query, passes configured batch size 25, caps 100 to 50, rejects unsafe values, and returns every ID from a multi-message Gmail fixture.

```java
assertEquals(List.of("message-1", "message-2"), batch.gmailMessageIds());
verify(client).findMessageIds("access-token", expectedQuery, 25);
```

- [ ] **Step 2: Verify finder tests fail for the missing abstraction**

Run: `./mvnw -Dtest=GmailOrderEmailCandidateFinderTest,GmailApiClientTest test`

Expected: compilation failure because the finder and batch client API do not exist.

- [ ] **Step 3: Implement the finder and bounded list call**

Move validated query construction out of `GmailSyncService`, add `batchSize` configuration defaulting to 25, cap it at 50, and map a null/empty Gmail message list to `List.of()`.

- [ ] **Step 4: Verify finder/client tests pass**

Run: `./mvnw -Dtest=GmailOrderEmailCandidateFinderTest,GmailApiClientTest test`

Expected: all finder and Gmail client tests pass.

- [ ] **Step 5: Commit Task 2**

```bash
git add src/main/java/com/indiedev/orders_hub/gmail src/main/resources/application.properties src/test/java/com/indiedev/orders_hub/gmail
git commit -m "refactor: isolate Gmail order discovery"
```

### Task 2: Add merchant identity and currency to parsed candidates

**Files:**
- Create: `src/main/java/com/indiedev/orders_hub/order/OrderStatus.java`
- Modify: `src/main/java/com/indiedev/orders_hub/gmail/dto/GmailOrderPreview.java`
- Modify: `src/main/java/com/indiedev/orders_hub/gmail/service/GmailOrderParser.java`
- Modify: `src/test/java/com/indiedev/orders_hub/gmail/service/GmailOrderParserTest.java`

**Interfaces:**
- Produces: `GmailOrderPreview` with `merchantKey`, `currency`, and `OrderStatus`.
- Produces: `int version()` returning parser version `1`.

- [ ] **Step 1: Write failing parser identity tests**

Assert `Amazon <orders@amazon.in>` produces display brand `Amazon`, merchant key `amazon.in`, `INR 1,499.00` produces currency `INR`, and a missing email domain produces a null merchant key.

```java
assertEquals("amazon.in", preview.merchantKey());
assertEquals("INR", preview.currency());
assertEquals(OrderStatus.SHIPPED, preview.status());
```

- [ ] **Step 2: Verify parser tests fail for the old DTO**

Run: `./mvnw -Dtest=GmailOrderParserTest test`

Expected: compilation failure because merchant key, currency, enum status, and parser version are missing.

- [ ] **Step 3: Implement the minimal parser additions**

Extract the sender domain independently from display name, map `INR`, `Rs`, and `₹` to `INR`, map `USD` and `$` to `USD`, return `OrderStatus`, and expose parser version `1`. Preserve all existing body/attachment and payment behavior.

- [ ] **Step 4: Verify parser tests pass**

Run: `./mvnw -Dtest=GmailOrderParserTest test`

Expected: all parser tests pass.

- [ ] **Step 5: Commit Task 3**

```bash
git add src/main/java/com/indiedev/orders_hub/gmail/dto/GmailOrderPreview.java src/main/java/com/indiedev/orders_hub/gmail/service/GmailOrderParser.java src/test/java/com/indiedev/orders_hub/gmail/service/GmailOrderParserTest.java
git commit -m "feat: identify Gmail order merchants"
```

### Task 4: Orchestrate batch imports during Google connection

**Files:**
- Modify: `src/main/java/com/indiedev/orders_hub/gmail/dto/GmailSyncPreview.java`
- Modify: `src/main/java/com/indiedev/orders_hub/gmail/service/GmailSyncService.java`
- Modify: `src/main/java/com/indiedev/orders_hub/gmail/service/GmailConnectionService.java`
- Modify: `src/test/java/com/indiedev/orders_hub/gmail/service/GmailSyncServiceTest.java`
- Modify: `src/test/java/com/indiedev/orders_hub/auth/service/AuthServiceTest.java`

**Interfaces:**
- Consumes: `CandidateBatch`, `GmailApiClient`, `GmailOrderParser`, and `GmailOrderImportService`.
- Produces: `GmailSyncPreview sync(ConnectedAccount account, String accessToken)`.
- Produces: batch response fields `candidateCount`, `savedCount`, `skippedCount`, `ignoredCount`, `failedCount`, and `orders`.

- [ ] **Step 1: Write failing mixed-batch sync tests**

Use three candidate IDs: one saved, one already processed, and one fetch failure. Assert processing continues, counts are `3/1/1/0/1`, only the saved preview is returned, and the skipped message is never fetched.

```java
assertEquals(3, result.candidateCount());
assertEquals(1, result.savedCount());
assertEquals(1, result.skippedCount());
assertEquals(1, result.failedCount());
```

Add an invalid candidate case asserting `ignoredCount=1` and no order is returned.

- [ ] **Step 2: Verify sync tests fail for the old single-preview flow**

Run: `./mvnw -Dtest=GmailSyncServiceTest test`

Expected: compilation failure because batch sync and result fields do not exist.

- [ ] **Step 3: Implement per-message orchestration**

Find one bounded batch, ask persistence whether each message should be processed before fetching it, fetch/parse/import independently, record safe failures, collect saved previews, and return immutable counts. Do not log message bodies or extracted fields.

- [ ] **Step 4: Update connection and auth fixtures**

Pass the stored `ConnectedAccount` into sync, retain existing sync success/failure marking, and update response fixtures to the new batch shape without exposing tokens.

- [ ] **Step 5: Verify focused integration tests pass**

Run: `./mvnw -Dtest=GmailSyncServiceTest,AuthServiceTest test`

Expected: all sync and authentication tests pass.

- [ ] **Step 6: Run the complete project verification**

Run: `./mvnw test`

Expected: Maven reports `BUILD SUCCESS` with zero failures and zero errors.

- [ ] **Step 7: Commit Task 4**

```bash
git add src/main src/test
git commit -m "feat: import Gmail orders during connection"
```
