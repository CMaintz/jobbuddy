package com.autoapplicant.adapter.web;

import com.autoapplicant.domain.common.CvParseException;
import com.autoapplicant.domain.common.NotFoundException;
import com.fasterxml.jackson.annotation.JsonInclude;
import java.time.Instant;
import java.util.stream.Collectors;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.*;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.web.ErrorResponse;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.context.request.WebRequest;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;

@RestControllerAdvice
public class GlobalExceptionHandler {

    private static final Logger log = LoggerFactory.getLogger(GlobalExceptionHandler.class);

    /** {@code code} is a stable machine-readable reason, present only where a client can act on it. */
    public record ApiError(int status, @JsonInclude(JsonInclude.Include.NON_NULL) String code,
                           String message, Instant timestamp, String path) {}

    @ExceptionHandler(NotFoundException.class)
    public ResponseEntity<ApiError> handleNotFound(NotFoundException ex, WebRequest req) {
        return error(HttpStatus.NOT_FOUND, ex.getMessage(), req);
    }

    @ExceptionHandler(IllegalArgumentException.class)
    public ResponseEntity<ApiError> handleBadRequest(IllegalArgumentException ex, WebRequest req) {
        return error(HttpStatus.BAD_REQUEST, ex.getMessage(), req);
    }

    @ExceptionHandler(IllegalStateException.class)
    public ResponseEntity<ApiError> handleConflict(IllegalStateException ex, WebRequest req) {
        return error(HttpStatus.CONFLICT, ex.getMessage(), req);
    }

    /**
     * A provider outage is 503 because the CV itself was fine; the other reasons are 422. The
     * cause's message is kept out of the warn line: it can carry CLI stderr or fragments of the CV.
     */
    @ExceptionHandler(CvParseException.class)
    public ResponseEntity<ApiError> handleCvParse(CvParseException ex, WebRequest req) {
        String causeType = ex.getCause() != null ? ex.getCause().getClass().getName() : "none";
        log.warn("CV parse failed on {}: reason={} cause={}",
                req.getDescription(false), ex.reason(), causeType);
        log.debug("CV parse failure cause", ex);
        HttpStatus status = ex.reason() == CvParseException.Reason.AI_UNAVAILABLE
                ? HttpStatus.SERVICE_UNAVAILABLE
                : HttpStatus.UNPROCESSABLE_ENTITY;
        return error(status, ex.reason().name(), ex.getMessage(), req);
    }

    @ExceptionHandler({SecurityException.class, AccessDeniedException.class})
    public ResponseEntity<ApiError> handleForbidden(RuntimeException ex, WebRequest req) {
        return error(HttpStatus.FORBIDDEN, ex.getMessage(), req);
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<ApiError> handleValidation(MethodArgumentNotValidException ex, WebRequest req) {
        String message = ex.getBindingResult().getFieldErrors().stream()
                .map(fe -> fe.getField() + ": " + fe.getDefaultMessage())
                .collect(Collectors.joining(", "));
        return error(HttpStatus.BAD_REQUEST, message, req);
    }

    @ExceptionHandler(HttpMessageNotReadableException.class)
    public ResponseEntity<ApiError> handleUnreadableBody(HttpMessageNotReadableException ex, WebRequest req) {
        return error(HttpStatus.BAD_REQUEST, "Malformed request body", req);
    }

    @ExceptionHandler(MethodArgumentTypeMismatchException.class)
    public ResponseEntity<ApiError> handleTypeMismatch(MethodArgumentTypeMismatchException ex, WebRequest req) {
        return error(HttpStatus.BAD_REQUEST, "Invalid value for '" + ex.getName() + "'", req);
    }

    /**
     * Spring's own request errors (405, 404 no handler, missing parameter, unsupported media
     * type, ResponseStatusException) carry their status; anything else is a real 500.
     */
    @ExceptionHandler(Exception.class)
    public ResponseEntity<ApiError> handleGeneral(Exception ex, WebRequest req) {
        if (ex instanceof ErrorResponse errorResponse) {
            HttpStatus status = HttpStatus.valueOf(errorResponse.getStatusCode().value());
            String detail = errorResponse.getBody().getDetail();
            return error(status, detail != null ? detail : status.getReasonPhrase(), req);
        }
        log.error("Unhandled exception on {}", req.getDescription(false), ex);
        return error(HttpStatus.INTERNAL_SERVER_ERROR, "Internal server error", req);
    }

    private ResponseEntity<ApiError> error(HttpStatus status, String message, WebRequest req) {
        return error(status, null, message, req);
    }

    private ResponseEntity<ApiError> error(HttpStatus status, String code, String message,
                                           WebRequest req) {
        return ResponseEntity.status(status).body(
                new ApiError(status.value(), code, message, Instant.now(),
                        req.getDescription(false).replace("uri=", "")));
    }
}
