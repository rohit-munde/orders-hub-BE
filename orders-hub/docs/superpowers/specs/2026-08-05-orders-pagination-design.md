# Orders Pagination Design

## Goal

Paginate `GET /api/v1/orders` using Spring Data `Pageable` while keeping the public JSON contract stable, reusable, newest-first, and isolated to the authenticated user.

## Scope

- Add zero-based `page` and `size` request parameters through Spring `Pageable`.
- Default to 20 orders and cap a page at 100 orders.
- Keep order sorting controlled by the backend:
  1. non-null `placedAt` values first;
  2. `placedAt DESC`;
  3. `id DESC` as the deterministic tie-breaker;
  4. null `placedAt` values last.
- Return a reusable `PageResponse<T>` with application-owned pagination metadata.
- Preserve `lastSyncedAt` in the orders response.
- Keep order items available without combining collection fetch joins with database pagination.
- Preserve the existing JWT user boundary and privacy-safe DTO mapping.

This change does not add client-controlled sorting, filtering, cursor pagination, or changes to the Gmail sync endpoint.

## HTTP Contract

The endpoint remains:

```http
GET /api/v1/orders?page=0&size=20
Authorization: Bearer <appToken>
```

Paging is zero-based: `page=0` is the first page.

The controller accepts `Pageable` with a default size of 20. The service rebuilds an unsorted `PageRequest` from only the requested page and size, preventing a client `sort` parameter from overriding the required business ordering. Spring's global maximum page size is 100.

The response uses the existing success envelope:

```json
{
  "success": true,
  "message": "Orders fetched successfully",
  "payload": {
    "lastSyncedAt": "2026-08-05T10:30:00Z",
    "orders": {
      "content": [
        {
          "id": 21,
          "merchantKey": "amazon.in",
          "brandName": "Amazon",
          "orderNo": "ORDER-123",
          "billAmount": 1499.00,
          "currency": "INR",
          "paid": true,
          "status": "SHIPPED",
          "placedAt": "2026-08-04T14:20:00Z",
          "items": []
        }
      ],
      "pagination": {
        "page": 0,
        "size": 20,
        "totalElements": 46,
        "totalPages": 3,
        "hasNext": true,
        "hasPrevious": false
      }
    }
  }
}
```

## Response Types

`PageResponse<T>` is application-owned and reusable:

```java
public record PageResponse<T>(
        List<T> content,
        PageMetadata pagination
) {
    public static <T> PageResponse<T> from(Page<T> page) { ... }
}
```

`PageMetadata` contains only the fields needed by clients:

```java
public record PageMetadata(
        int page,
        int size,
        long totalElements,
        int totalPages,
        boolean hasNext,
        boolean hasPrevious
) {
    public static PageMetadata from(Page<?> page) { ... }
}
```

The public response does not expose Spring's raw `Page`, `Pageable`, `Sort`, or `PagedModel` JSON. That keeps frontend models independent of framework serialization details.

`OrderListResponse` becomes:

```java
public record OrderListResponse(
        Instant lastSyncedAt,
        PageResponse<OrderResponse> orders
) { ... }
```

## Persistence

The repository returns `Page<Order>` and uses a separate count query:

```java
Page<Order> findPageForUser(long userId, Pageable pageable);
```

The content query keeps the fixed newest-first ordering. The count query counts only orders belonging to the authenticated user and does not join order items.

The current `@EntityGraph(attributePaths = "orderItems")` is removed from the paginated query. Combining a pageable query with a fetched one-to-many collection can force Hibernate to paginate in memory. Instead, `Order.orderItems` uses Hibernate `@BatchSize(size = 50)`, and DTO mapping remains inside the read-only transaction. This keeps database-level order pagination while avoiding one query per order.

## Service Flow

1. Receive JWT-derived `userId` and the requested `Pageable`.
2. Rebuild a safe `PageRequest` using page and size only.
3. Load the connected account's `lastSyncAt`.
4. Query the authenticated user's order page.
5. Map the page to `OrderResponse` using `Page.map(...)`.
6. Wrap it with `PageResponse.from(...)` and return `OrderListResponse`.

No entity is returned directly from the API.

## Configuration and Validation

```properties
spring.data.web.pageable.default-page-size=20
spring.data.web.pageable.max-page-size=100
```

Spring rejects structurally invalid paging values. The maximum prevents oversized queries. The frontend should omit parameters for the default first page and request subsequent pages using `page=1`, `page=2`, and so on.

## Error Handling

- Missing or invalid application JWT remains HTTP 401.
- Missing JWT `userId` remains HTTP 400 through the existing global handler.
- Invalid paging input uses the existing global error response.
- An empty or out-of-range page is a successful response with empty `content` and accurate pagination metadata.

## Testing

- Repository test: page size, totals, total pages, user isolation, fixed ordering, null-last behavior, and deterministic ties.
- Response test: metadata mapping and immutable content.
- Service test: requested page/size are forwarded, client sorting is removed, DTO fields are mapped, and `lastSyncedAt` is preserved.
- Controller test: default paging, explicit page/size, JWT user identity, stable response JSON, and anonymous rejection.
- Full regression suite: Gmail synchronization, import idempotency, token refresh, and existing global response/error handling remain green.

## Compatibility

The endpoint URL and authorization are unchanged, but the successful list payload changes from a plain `orders` array to a paginated `orders` object containing `content` and `pagination`. The frontend must update its response model before consuming this backend version.
