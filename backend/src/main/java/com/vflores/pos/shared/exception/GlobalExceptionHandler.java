package com.vflores.pos.shared.exception;

import com.vflores.pos.adminauthorizations.application.AdminAuthorizationRejectedException;
import com.vflores.pos.adminauthorizations.application.AdminAuthorizationRequiredException;
import com.vflores.pos.auth.application.AuthenticationRateLimitExceededException;
import com.vflores.pos.shared.logging.DiagnosticLogSupport;
import jakarta.validation.ConstraintViolationException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.authentication.DisabledException;
import org.springframework.security.authentication.LockedException;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.validation.FieldError;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import java.util.List;

@RestControllerAdvice
public class GlobalExceptionHandler {

    private static final Logger LOGGER = LoggerFactory.getLogger(GlobalExceptionHandler.class);

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<ApiErrorResponse> handleValidation(MethodArgumentNotValidException ex) {
        logControlled("VALIDATION_ERROR", ex, "Request validation failed");
        List<ApiErrorResponse.FieldErrorItem> fieldErrors = ex.getBindingResult()
                .getFieldErrors()
                .stream()
                .map(this::toFieldError)
                .toList();

        return ResponseEntity.badRequest()
                .body(ApiErrorResponse.of("VALIDATION_ERROR", "Invalid request data", fieldErrors));
    }

    @ExceptionHandler(ConstraintViolationException.class)
    public ResponseEntity<ApiErrorResponse> handleConstraint(ConstraintViolationException ex) {
        logControlled("VALIDATION_ERROR", ex, "Constraint validation failed");
        return ResponseEntity.badRequest()
                .body(ApiErrorResponse.of("VALIDATION_ERROR", ex.getMessage(), List.of()));
    }

    @ExceptionHandler(ResourceNotFoundException.class)
    public ResponseEntity<ApiErrorResponse> handleNotFound(ResourceNotFoundException ex) {
        logControlled("RESOURCE_NOT_FOUND", ex, DiagnosticLogSupport.safeMessage(ex));
        return ResponseEntity.status(HttpStatus.NOT_FOUND)
                .body(ApiErrorResponse.of("RESOURCE_NOT_FOUND", ex.getMessage(), List.of()));
    }

    @ExceptionHandler(ConflictException.class)
    public ResponseEntity<ApiErrorResponse> handleConflict(ConflictException ex) {
        logControlled("DUPLICATE_RESOURCE", ex, DiagnosticLogSupport.safeMessage(ex));
        return ResponseEntity.status(HttpStatus.CONFLICT)
                .body(ApiErrorResponse.of("DUPLICATE_RESOURCE", ex.getMessage(), List.of()));
    }

    @ExceptionHandler(BadCredentialsException.class)
    public ResponseEntity<ApiErrorResponse> handleBadCredentials(BadCredentialsException ex) {
        logControlled("AUTH_INVALID_CREDENTIALS", ex, "Authentication rejected");
        return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                .body(ApiErrorResponse.of("AUTH_INVALID_CREDENTIALS", "Invalid credentials", List.of()));
    }

    @ExceptionHandler(AuthenticationRateLimitExceededException.class)
    public ResponseEntity<ApiErrorResponse> handleAuthenticationRateLimit(
            AuthenticationRateLimitExceededException ex
    ) {
        logControlled("AUTH_RATE_LIMITED", ex, "Authentication rate limit reached");
        return ResponseEntity.status(HttpStatus.TOO_MANY_REQUESTS)
                .body(ApiErrorResponse.of(
                        "AUTH_RATE_LIMITED",
                        "Authentication temporarily unavailable. Try again later",
                        List.of()
                ));
    }

    @ExceptionHandler(AuthenticationException.class)
    public ResponseEntity<ApiErrorResponse> handleAuthentication(AuthenticationException ex) {
        logControlled("AUTH_INVALID_CREDENTIALS", ex, "Authentication rejected");
        return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                .body(ApiErrorResponse.of("AUTH_INVALID_CREDENTIALS", "Invalid credentials", List.of()));
    }

    @ExceptionHandler(AccessDeniedException.class)
    public ResponseEntity<ApiErrorResponse> handleAccessDenied(AccessDeniedException ex) {
        logControlled("ACCESS_DENIED", ex, "Authorization rejected");
        return ResponseEntity.status(HttpStatus.FORBIDDEN)
                .body(ApiErrorResponse.of(
                        "ACCESS_DENIED",
                        "You do not have permission for this action",
                        List.of()
                ));
    }

    @ExceptionHandler(AdminAuthorizationRequiredException.class)
    public ResponseEntity<ApiErrorResponse> handleAdminAuthorizationRequired(
            AdminAuthorizationRequiredException ex
    ) {
        logControlled("ADMIN_AUTHORIZATION_REQUIRED", ex, "Temporary administrator authorization required");
        return ResponseEntity.status(HttpStatus.FORBIDDEN)
                .body(ApiErrorResponse.of(
                        "ADMIN_AUTHORIZATION_REQUIRED",
                        "Temporary administrator authorization is required",
                        List.of()
                ));
    }

    @ExceptionHandler(AdminAuthorizationRejectedException.class)
    public ResponseEntity<ApiErrorResponse> handleAdminAuthorizationRejected(
            AdminAuthorizationRejectedException ex
    ) {
        logControlled("ADMIN_AUTHORIZATION_REJECTED", ex, "Temporary administrator authorization rejected");
        return ResponseEntity.status(HttpStatus.FORBIDDEN)
                .body(ApiErrorResponse.of(
                        "ADMIN_AUTHORIZATION_REJECTED",
                        ex.getMessage(),
                        List.of()
                ));
    }

    @ExceptionHandler({LockedException.class, DisabledException.class})
    public ResponseEntity<ApiErrorResponse> handleDisabledUser(RuntimeException ex) {
        logControlled("AUTH_INVALID_CREDENTIALS", ex, "Authentication rejected");
        return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                .body(ApiErrorResponse.of("AUTH_INVALID_CREDENTIALS", "Invalid credentials", List.of()));
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<ApiErrorResponse> handleGeneric(Exception ex) {
        LOGGER.error(
                "code=INTERNAL_ERROR user={} exceptionType={} technicalMessage={} stackTrace={}",
                DiagnosticLogSupport.currentUser(),
                ex.getClass().getName(),
                DiagnosticLogSupport.safeMessage(ex),
                DiagnosticLogSupport.safeStackTrace(ex)
        );
        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                .body(ApiErrorResponse.of("INTERNAL_ERROR", "Unexpected server error", List.of()));
    }

    private void logControlled(String code, Exception ex, String technicalMessage) {
        LOGGER.warn(
                "code={} user={} exceptionType={} technicalMessage={}",
                code,
                DiagnosticLogSupport.currentUser(),
                ex.getClass().getName(),
                technicalMessage
        );
    }

    private ApiErrorResponse.FieldErrorItem toFieldError(FieldError fieldError) {
        return new ApiErrorResponse.FieldErrorItem(fieldError.getField(), fieldError.getDefaultMessage());
    }
}
