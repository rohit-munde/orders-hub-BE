package com.indiedev.orders_hub.common;

import com.indiedev.orders_hub.gmail.exception.ConnectedAccountConflictException;
import com.indiedev.orders_hub.gmail.exception.GmailApiException;
import com.indiedev.orders_hub.gmail.exception.GoogleOAuthExchangeException;
import com.indiedev.orders_hub.gmail.exception.RefreshTokenMissingException;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.authentication.AuthenticationCredentialsNotFoundException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import java.time.Instant;

@RestControllerAdvice
public class ApiExceptionHandler {

    @ExceptionHandler(IllegalArgumentException.class)
    ResponseEntity<ApiError> handleIllegalArgument(
            IllegalArgumentException exception,
            HttpServletRequest request
    ) {
        String message = exception.getMessage() == null
                ? "Invalid request"
                : exception.getMessage();

        return error(HttpStatus.BAD_REQUEST, message, request);
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    ResponseEntity<ApiError> handleValidation(
            MethodArgumentNotValidException exception,
            HttpServletRequest request
    ) {
        String message = exception.getBindingResult().getFieldErrors().stream()
                .findFirst()
                .map(error -> error.getDefaultMessage())
                .orElse("Invalid request");

        return error(HttpStatus.BAD_REQUEST, message, request);
    }

    @ExceptionHandler(RefreshTokenMissingException.class)
    ResponseEntity<ApiError> handleMissingRefreshToken(
            RefreshTokenMissingException exception,
            HttpServletRequest request
    ) {
        return error(HttpStatus.BAD_REQUEST, exception.getMessage(), request);
    }

    @ExceptionHandler(ConnectedAccountConflictException.class)
    ResponseEntity<ApiError> handleConnectedAccountConflict(
            ConnectedAccountConflictException exception,
            HttpServletRequest request
    ) {
        return error(HttpStatus.CONFLICT, exception.getMessage(), request);
    }

    @ExceptionHandler(GoogleOAuthExchangeException.class)
    ResponseEntity<ApiError> handleOAuthExchange(
            GoogleOAuthExchangeException exception,
            HttpServletRequest request
    ) {
        return error(HttpStatus.BAD_GATEWAY, exception.getMessage(), request);
    }

    @ExceptionHandler(GmailApiException.class)
    ResponseEntity<ApiError> handleGmailApi(
            GmailApiException exception,
            HttpServletRequest request
    ) {
        return error(HttpStatus.BAD_GATEWAY, exception.getMessage(), request);
    }

    @ExceptionHandler(AuthenticationCredentialsNotFoundException.class)
    ResponseEntity<ApiError> handleAuthentication(
            AuthenticationCredentialsNotFoundException exception,
            HttpServletRequest request
    ) {
        return error(HttpStatus.UNAUTHORIZED, exception.getMessage(), request);
    }

    private ResponseEntity<ApiError> error(
            HttpStatus status,
            String message,
            HttpServletRequest request
    ) {
        return ResponseEntity.status(status).body(new ApiError(
                Instant.now(),
                status.value(),
                status.getReasonPhrase(),
                message,
                request.getRequestURI()
        ));
    }

    record ApiError(
            Instant timestamp,
            int status,
            String error,
            String message,
            String path
    ) {
    }
}
