# Business Exception Global Handler Design

The application will use `GlobalExceptionHandling` as its only application-level REST exception handler. The legacy `common/ApiExceptionHandler` will be removed.

The three project-defined exceptions will extend `BusinessException` and retain their existing response semantics: account conflicts and reconnect-required errors return `409 CONFLICT`, while Google API failures return `502 BAD_GATEWAY`. Existing standard Java and Spring exceptions remain unchanged.

Verification will cover the custom exception hierarchy and HTTP status values, followed by the complete Maven test suite.
