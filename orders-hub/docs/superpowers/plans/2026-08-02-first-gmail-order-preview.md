# First Gmail Order Preview Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Return a best-effort order JSON preview for the first email found by the existing Gmail order search during Google login.

**Architecture:** Replace the metadata-page preview with a single-result flow. `GmailApiClient` finds one ID and decodes one full Gmail message, `GmailOrderParser` maps normalized content to an immutable preview DTO, and `GmailSyncService` coordinates both operations without persistence.

**Tech Stack:** Java 21, Spring Boot 4.1, Spring `RestClient`, Jackson, JUnit 5, Mockito, Maven.

## Global Constraints

- Keep the implementation deterministic, minimal, and readable.
- Do not add an endpoint, database persistence, token refresh, AI calls, attachment downloads, or merchant-specific parsers.
- Do not log message bodies or extracted private content.
- Request only one Gmail search result.
- Return null optional values and an empty `orderItems` list when extraction has no match.

---

### Task 1: Fetch and decode one full Gmail message

**Files:**
- Create: `src/main/java/com/indiedev/orders_hub/gmail/dto/GmailMessageContent.java`
- Modify: `src/main/java/com/indiedev/orders_hub/gmail/client/GmailApiClient.java`
- Modify: `src/test/java/com/indiedev/orders_hub/gmail/client/GmailApiClientTest.java`

**Interfaces:**
- Produces: `Optional<String> findFirstMessageId(String accessToken, String query)`.
- Produces: `GmailMessageContent getFullMessage(String accessToken, String gmailMessageId)`.
- Produces: `record GmailMessageContent(String gmailMessageId, String subject, String from, String body)`.

- [ ] **Step 1: Write failing client tests**

Add tests with literal Gmail fixtures that require `maxResults=1`, `format=full`, recursive `text/plain` Base64URL decoding, and HTML fallback. Assert the returned ID, headers, and readable body.

```java
assertEquals("message-1", client.findFirstMessageId("access-token", "subject:order").orElseThrow());
GmailMessageContent message = client.getFullMessage("access-token", "message-1");
assertEquals("Order number: ORDER-123", message.body());
```

- [ ] **Step 2: Verify the tests fail for the missing API**

Run: `./mvnw -Dtest=GmailApiClientTest test`

Expected: compilation failure because `findFirstMessageId`, `getFullMessage`, and `GmailMessageContent` do not exist.

- [ ] **Step 3: Implement the minimal Gmail client behavior**

Use `users/me/messages?maxResults=1&q=...`, map the first ID to `Optional`, and call `users/me/messages/{id}?format=full`. Recursively search MIME parts for `text/plain`, fall back to `text/html`, decode with `Base64.getUrlDecoder()`, and normalize HTML using `HtmlUtils.htmlUnescape` after removing tags.

- [ ] **Step 4: Verify the client tests pass**

Run: `./mvnw -Dtest=GmailApiClientTest test`

Expected: all `GmailApiClientTest` tests pass.

### Task 2: Parse decoded content into order JSON

**Files:**
- Create: `src/main/java/com/indiedev/orders_hub/gmail/dto/GmailOrderPreview.java`
- Create: `src/main/java/com/indiedev/orders_hub/gmail/service/GmailOrderParser.java`
- Create: `src/test/java/com/indiedev/orders_hub/gmail/service/GmailOrderParserTest.java`

**Interfaces:**
- Consumes: `GmailMessageContent` from Task 1.
- Produces: `GmailOrderPreview parse(GmailMessageContent message)`.
- Produces: `record GmailOrderPreview(String gmailMessageId, String brandName, String orderNo, BigDecimal billAmount, Boolean paid, String otp, String status, List<OrderItemPreview> orderItems)`.

- [ ] **Step 1: Write failing parser tests**

Use a literal sender and body fixture and assert independently derived values:

```java
GmailOrderPreview preview = parser.parse(new GmailMessageContent(
        "message-1", "Your order shipped", "Amazon <orders@amazon.in>",
        "Order number: ORDER-123\nTotal amount: INR 1,499.00\nPayment successful\nOTP: 482731\nYour order has shipped"
));
assertEquals("Amazon", preview.brandName());
assertEquals("ORDER-123", preview.orderNo());
assertEquals(new BigDecimal("1499.00"), preview.billAmount());
assertEquals(Boolean.TRUE, preview.paid());
assertEquals("482731", preview.otp());
assertEquals("SHIPPED", preview.status());
assertEquals(List.of(), preview.orderItems());
```

Add one partial-message test asserting null optional values and `UNKNOWN`.

- [ ] **Step 2: Verify the tests fail for the missing parser**

Run: `./mvnw -Dtest=GmailOrderParserTest test`

Expected: compilation failure because the parser and preview DTO do not exist.

- [ ] **Step 3: Implement explicit extraction patterns**

Use small compiled patterns for sender name/domain, labeled order number, labeled amount, payment state, and OTP. Derive status from explicit keywords with cancellation and delivery states checked before shipment/confirmation. Always return `List.of()` for items.

- [ ] **Step 4: Verify parser tests pass**

Run: `./mvnw -Dtest=GmailOrderParserTest test`

Expected: all `GmailOrderParserTest` tests pass.

### Task 3: Wire the first result into the login preview and remove superseded metadata code

**Files:**
- Modify: `src/main/java/com/indiedev/orders_hub/gmail/dto/GmailSyncPreview.java`
- Modify: `src/main/java/com/indiedev/orders_hub/gmail/service/GmailSyncService.java`
- Modify: `src/main/java/com/indiedev/orders_hub/gmail/service/GmailConnectionService.java`
- Modify: `src/main/java/com/indiedev/orders_hub/gmail/config/GmailSearchProperties.java`
- Modify: `src/main/resources/application.properties`
- Delete: `src/main/java/com/indiedev/orders_hub/gmail/dto/GmailMessageSummary.java`
- Modify: `src/test/java/com/indiedev/orders_hub/gmail/service/GmailSyncServiceTest.java`
- Modify: `src/test/java/com/indiedev/orders_hub/auth/service/AuthServiceTest.java`

**Interfaces:**
- Consumes: `findFirstMessageId`, `getFullMessage`, and `GmailOrderParser.parse`.
- Produces: `record GmailSyncPreview(String query, String gmailMessageId, GmailOrderPreview orderPreview)`.
- Produces: `GmailSyncPreview previewFirstOrder(String accessToken)`.

- [ ] **Step 1: Write failing sync tests for one result and no result**

Assert that one ID is fetched and parsed, and that no ID returns `new GmailSyncPreview(query, null, null)` without a full-message call.

```java
assertEquals("gmail-1", preview.gmailMessageId());
assertSame(orderPreview, preview.orderPreview());
```

- [ ] **Step 2: Verify the sync tests fail for the old contract**

Run: `./mvnw -Dtest=GmailSyncServiceTest test`

Expected: compilation failure because `previewFirstOrder`, `gmailMessageId`, and `orderPreview` do not exist.

- [ ] **Step 3: Implement the minimal coordinator and remove unused metadata pagination**

Build the existing validated query, ask the client for one ID, return null preview fields when absent, otherwise fetch, parse, and return the result. Update `GmailConnectionService`, fixtures, and configuration; delete unused metadata DTO/method and the unused max-results property.

- [ ] **Step 4: Verify focused tests pass**

Run: `./mvnw -Dtest=GmailSyncServiceTest,AuthServiceTest test`

Expected: all focused tests pass.

- [ ] **Step 5: Verify the complete project**

Run: `./mvnw test`

Expected: Maven reports `BUILD SUCCESS` with zero test failures and zero test errors.

- [ ] **Step 6: Commit the implementation**

```bash
git add src/main src/test docs/superpowers/plans/2026-08-02-first-gmail-order-preview.md
git commit -m "feat: preview first Gmail order"
```
