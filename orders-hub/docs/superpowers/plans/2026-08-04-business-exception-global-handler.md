# Business Exception Global Handler Implementation Plan

> **For agentic workers:** Execute task-by-task with test-first verification.

**Goal:** Make `GlobalExceptionHandling` the sole REST exception handler and make all project-defined exceptions extend `BusinessException`.

**Architecture:** `BusinessException` remains the shared base carrying an `HttpStatus`. Each domain exception supplies its existing status and message to that base. The duplicate `ApiExceptionHandler` is deleted; unrelated standard exception handling is not changed.

**Tech Stack:** Java, Spring Boot, JUnit 5, Maven.

## Global Constraints

- Preserve existing exception messages and constructor behavior.
- Do not convert standard Java/Spring exceptions to `BusinessException`.
- Do not modify unrelated staged or unstaged user changes.

### Task 1: Update exception hierarchy and handler ownership

**Files:**
- Modify: `src/main/java/com/indiedev/orders_hub/exception/ConnectedAccountConflictException.java`
- Modify: `src/main/java/com/indiedev/orders_hub/exception/GmailConnectionRequiredException.java`
- Modify: `src/main/java/com/indiedev/orders_hub/exception/GoogleApiException.java`
- Delete: `src/main/java/com/indiedev/orders_hub/common/ApiExceptionHandler.java`
- Test: `src/test/java/com/indiedev/orders_hub/exception/BusinessExceptionHierarchyTest.java`

- [x] Write a failing test that constructs each custom exception, asserts it is a `BusinessException`, and verifies `CONFLICT`/`BAD_GATEWAY` statuses.
- [x] Run the focused test and confirm it fails because the subclasses currently extend `RuntimeException` and lack the required base constructor.
- [x] Change the subclasses to call `BusinessException` with their existing messages, statuses, and Google API cause.
- [x] Delete the legacy `ApiExceptionHandler` so `GlobalExceptionHandling` is the only handler.
- [x] Run the focused test, then `./mvnw test`, and confirm all tests pass.
