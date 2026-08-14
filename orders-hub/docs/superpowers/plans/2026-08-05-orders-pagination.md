# Orders Pagination Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task with review checkpoints.

**Goal:** Return the authenticated user's orders through a stable, zero-based paginated API, newest orders first, while preserving sync metadata and preventing clients from changing the backend-defined ordering.

**Architecture:** Spring MVC binds `page` and `size` into a `Pageable`. The query service rebuilds an unsorted `PageRequest`, the repository applies a fixed JPQL order and count query, and the service maps the resulting `Page<Order>` into application-owned pagination DTOs inside a read-only transaction. The controller wraps the result in the existing `ApiSuccessResponse` contract.

**Tech stack:** Java, Spring Boot MVC, Spring Data JPA, Hibernate, JUnit 5, Mockito, MockMvc, Maven.

**Global constraints:** Keep `GET /api/v1/orders` authenticated and user-scoped. Default to page `0` and size `20`, cap size at `100`, ignore client sort parameters, place null `placedAt` values last, and use `id DESC` as the deterministic tie-breaker. Do not change Gmail sync behavior. Preserve and complete the existing staged `PageResponse.java` draft.

---

## Task 1: Create stable application-owned pagination DTOs

**Files:**

- Modify: `src/main/java/com/indiedev/orders_hub/response/PageResponse.java`
- Create: `src/main/java/com/indiedev/orders_hub/response/PageMetadata.java`
- Create: `src/test/java/com/indiedev/orders_hub/response/PageResponseTest.java`

### Step 1: Write the failing DTO tests

Create tests using `PageImpl` and `PageRequest` that verify:

- `PageResponse.from(page)` copies the page content.
- Metadata exposes `page`, `size`, `totalElements`, `totalPages`, `hasNext`, and `hasPrevious`.
- The returned content cannot be modified.

Use assertions equivalent to:

```java
Page<String> page = new PageImpl<>(
        List.of("order-3", "order-2"),
        PageRequest.of(1, 2),
        5
);

PageResponse<String> response = PageResponse.from(page);

assertThat(response.content()).containsExactly("order-3", "order-2");
assertThat(response.pagination()).isEqualTo(
        new PageMetadata(1, 2, 5, 3, true, true)
);
assertThatThrownBy(() -> response.content().add("order-1"))
        .isInstanceOf(UnsupportedOperationException.class);
```

### Step 2: Run the focused test and confirm RED

Run:

```bash
./mvnw test -Dtest=PageResponseTest
```

Expected: compilation/test failure because the application-owned `PageMetadata` and final `PageResponse` contract do not exist yet.

### Step 3: Implement the minimal DTOs

Replace the Spring web `PagedModel.PageMetadata` dependency in the staged draft with application-owned records:

```java
public record PageMetadata(
        int page,
        int size,
        long totalElements,
        int totalPages,
        boolean hasNext,
        boolean hasPrevious
) {
    public static PageMetadata from(Page<?> page) {
        return new PageMetadata(
                page.getNumber(),
                page.getSize(),
                page.getTotalElements(),
                page.getTotalPages(),
                page.hasNext(),
                page.hasPrevious()
        );
    }
}
```

```java
public record PageResponse<T>(
        List<T> content,
        PageMetadata pagination
) {
    public PageResponse {
        content = List.copyOf(content);
    }

    public static <T> PageResponse<T> from(Page<T> page) {
        return new PageResponse<>(page.getContent(), PageMetadata.from(page));
    }
}
```

Require non-null `page` and `content` only if the focused tests show that an explicit guard improves the contract; avoid extra abstractions.

### Step 4: Run the focused test and confirm GREEN

Run:

```bash
./mvnw test -Dtest=PageResponseTest
```

Expected: PASS.

### Step 5: Commit Task 1 intentionally

Stage only the three Task 1 files and commit:

```bash
git add src/main/java/com/indiedev/orders_hub/response/PageResponse.java src/main/java/com/indiedev/orders_hub/response/PageMetadata.java src/test/java/com/indiedev/orders_hub/response/PageResponseTest.java
git commit -m "feat: add stable pagination response"
```

---

## Task 2: Page the user-scoped order query with fixed ordering

**Files:**

- Modify: `src/main/java/com/indiedev/orders_hub/repository/OrderRepository.java`
- Modify: `src/main/java/com/indiedev/orders_hub/entity/Order.java`
- Modify: `src/main/java/com/indiedev/orders_hub/response/OrderListResponse.java`
- Modify: `src/main/java/com/indiedev/orders_hub/service/OrderQueryService.java`
- Modify: `src/test/java/com/indiedev/orders_hub/repository/OrderRepositoryOrderingTest.java`
- Modify: `src/test/java/com/indiedev/orders_hub/service/OrderQueryServiceTest.java`

### Step 1: Change repository tests to describe page behavior

Update `OrderRepositoryOrderingTest` to persist orders for at least two users and enough orders for two pages. Test:

- Only the requested user's orders are counted and returned.
- Non-null `placedAt` values come before null values.
- Newer `placedAt` values come first.
- Equal `placedAt` values use `id DESC`.
- Page metadata reports the correct total and second-page content.

Call the intended API:

```java
Page<Order> page = orderRepository.findPageForUser(
        userId,
        PageRequest.of(0, 2)
);
```

### Step 2: Change service tests to describe safe Pageable handling

Update `OrderQueryServiceTest` to pass a sorted client pageable:

```java
Pageable clientPageable = PageRequest.of(
        1,
        20,
        Sort.by("merchantName").ascending()
);
```

Capture the pageable passed to the repository and assert:

- Page number remains `1`.
- Page size remains `20`.
- Sort is `Sort.unsorted()`.
- `lastSyncedAt` is preserved.
- The mapped response contains `PageResponse<OrderResponse>` and correct metadata.

### Step 3: Run repository and service tests and confirm RED

Run:

```bash
./mvnw test -Dtest=OrderRepositoryOrderingTest,OrderQueryServiceTest
```

Expected: compilation/test failures because the repository and service still use `List<Order>`.

### Step 4: Implement the paginated repository query

Replace the list query with:

```java
@Query(
        value = """
                select o
                from Order o
                where o.user.id = :userId
                order by
                    case when o.placedAt is null then 1 else 0 end,
                    o.placedAt desc,
                    o.id desc
                """,
        countQuery = """
                select count(o)
                from Order o
                where o.user.id = :userId
                """
)
Page<Order> findPageForUser(@Param("userId") long userId, Pageable pageable);
```

Remove the to-many `@EntityGraph(attributePaths = "orderItems")` from this paginated method. Add Hibernate `@BatchSize(size = 50)` to the `Order.orderItems` collection so item loading remains bounded while mapping inside the transaction.

### Step 5: Implement the paginated service mapping

Change the public service method to:

```java
@Transactional(readOnly = true)
public OrderListResponse getOrders(long userId, Pageable pageable) {
    Pageable safePageable = PageRequest.of(
            pageable.getPageNumber(),
            pageable.getPageSize()
    );
    Page<OrderResponse> orders = orderRepository
            .findPageForUser(userId, safePageable)
            .map(this::toResponse);

    return new OrderListResponse(
            gmailConnectionRepository.findLastSyncedAtByUserId(userId).orElse(null),
            PageResponse.from(orders)
    );
}
```

Update `OrderListResponse` to:

```java
public record OrderListResponse(
        Instant lastSyncedAt,
        PageResponse<OrderResponse> orders
) {}
```

Use the existing entity-to-response mapping method without introducing another mapper layer.

### Step 6: Run focused tests and confirm GREEN

Run:

```bash
./mvnw test -Dtest=OrderRepositoryOrderingTest,OrderQueryServiceTest
```

Expected: PASS, with no Hibernate warning about in-memory pagination over a collection fetch.

### Step 7: Commit Task 2

```bash
git add src/main/java/com/indiedev/orders_hub/repository/OrderRepository.java src/main/java/com/indiedev/orders_hub/entity/Order.java src/main/java/com/indiedev/orders_hub/response/OrderListResponse.java src/main/java/com/indiedev/orders_hub/service/OrderQueryService.java src/test/java/com/indiedev/orders_hub/repository/OrderRepositoryOrderingTest.java src/test/java/com/indiedev/orders_hub/service/OrderQueryServiceTest.java
git commit -m "feat: paginate user orders query"
```

---

## Task 3: Expose the standard paginated HTTP response

**Files:**

- Modify: `src/main/java/com/indiedev/orders_hub/controller/OrderController.java`
- Modify: `src/main/resources/application.properties`
- Modify: `src/test/java/com/indiedev/orders_hub/controller/OrderControllerTest.java`

### Step 1: Update controller tests for the final API contract

Update `OrderControllerTest` to mock a paginated response and assert:

- `GET /api/v1/orders` returns HTTP 200.
- JSON has `success: true` and message `Orders fetched successfully`.
- `payload.lastSyncedAt` is present.
- Orders live at `payload.orders.content`.
- Pagination lives at `payload.orders.pagination` with all six fields.
- With no query parameters, the service receives page `0`, size `20`.
- With `?page=2&size=10&sort=merchantName,asc`, the controller passes page `2`, size `10`; the service test remains responsible for proving sort is stripped.

Use the final response shape:

```json
{
  "success": true,
  "message": "Orders fetched successfully",
  "payload": {
    "lastSyncedAt": "2026-08-05T10:00:00Z",
    "orders": {
      "content": [],
      "pagination": {
        "page": 0,
        "size": 20,
        "totalElements": 0,
        "totalPages": 0,
        "hasNext": false,
        "hasPrevious": false
      }
    }
  }
}
```

### Step 2: Run the controller test and confirm RED

Run:

```bash
./mvnw test -Dtest=OrderControllerTest
```

Expected: failure because the controller still returns the old unwrapped list response and does not accept `Pageable`.

### Step 3: Implement the controller and global page limits

Accept `Pageable` on the GET endpoint, using `@PageableDefault(size = 20)` for readable endpoint intent, then return:

```java
return ResponseEntity.ok(new ApiSuccessResponse<>(
        "Orders fetched successfully",
        orderQueryService.getOrders(userId, pageable)
));
```

Add:

```properties
spring.data.web.pageable.default-page-size=20
spring.data.web.pageable.max-page-size=100
```

Do not add endpoint-specific sort or filter parameters.

### Step 4: Run the controller test and confirm GREEN

Run:

```bash
./mvnw test -Dtest=OrderControllerTest
```

Expected: PASS.

### Step 5: Run complete verification

Run:

```bash
./mvnw test
git diff --check
git status --short
```

Confirm all tests pass, the repository query stays user-scoped, the response format matches the approved design, and no unrelated files were modified.

### Step 6: Commit Task 3

```bash
git add src/main/java/com/indiedev/orders_hub/controller/OrderController.java src/main/resources/application.properties src/test/java/com/indiedev/orders_hub/controller/OrderControllerTest.java
git commit -m "feat: expose paginated orders API"
```

### Step 7: Final review

Run `./mvnw test` again after the commit and inspect `git log -4 --oneline` plus `git status --short`. Report the endpoint contract and any remaining pre-existing working-tree changes. Do not push without explicit user approval.
