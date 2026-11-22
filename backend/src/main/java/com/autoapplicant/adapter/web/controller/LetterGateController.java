package com.autoapplicant.adapter.web.controller;

import com.autoapplicant.adapter.security.SecurityContextHelper;
import com.autoapplicant.domain.document.structured.LetterGateNoVerdictException;
import com.autoapplicant.domain.document.structured.StructuredDocument;
import com.autoapplicant.port.in.document.RecheckLetterUseCase;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import java.util.UUID;
import java.util.concurrent.CompletionException;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.context.request.async.DeferredResult;

/** The letter gate on demand. Under {@code /api/v1/ai} so the AI rate limit covers it. */
@RestController
@RequestMapping("/api/v1/ai/documents")
@Tag(name = "AI")
public class LetterGateController {

    private final RecheckLetterUseCase recheck;
    private final SecurityContextHelper secCtx;
    private final long requestTimeoutMillis;

    public LetterGateController(RecheckLetterUseCase recheck, SecurityContextHelper secCtx,
                                @Value("${app.ai.request-timeout-seconds:1080}") long requestTimeoutSeconds) {
        this.recheck = recheck;
        this.secCtx = secCtx;
        this.requestTimeoutMillis = requestTimeoutSeconds * 1000L;
    }

    @Operation(summary = "Check a saved letter against its posting again and store the verdict")
    @ApiResponses({
            @ApiResponse(responseCode = "400", description = "Document not found for this user"),
            @ApiResponse(responseCode = "409", description = "Not a letter written to a posting"),
            @ApiResponse(responseCode = "503", description = "The gate gave no verdict; the stored one is kept")
    })
    @PostMapping("/{id}/letter-gate")
    public DeferredResult<ResponseEntity<StructuredDocument>> recheckLetter(@PathVariable UUID id) {
        DeferredResult<ResponseEntity<StructuredDocument>> result = new DeferredResult<>(requestTimeoutMillis);
        result.onTimeout(() -> result.setErrorResult(
                ResponseEntity.status(504).body("The letter check timed out. Please try again.")));
        recheck.recheck(secCtx.getCurrentUserId(), id)
                .thenAccept(document -> result.setResult(ResponseEntity.ok(document)))
                .exceptionally(e -> {
                    // The stage wraps the use case's exception; unwrapped, the exception handler
                    // answers 400 or 409 for it instead of a generic 500.
                    result.setErrorResult(e instanceof CompletionException && e.getCause() != null ? e.getCause() : e);
                    return null;
                });
        return result;
    }

    @ExceptionHandler(LetterGateNoVerdictException.class)
    public ResponseEntity<String> noVerdict(LetterGateNoVerdictException e) {
        return ResponseEntity.status(HttpStatus.SERVICE_UNAVAILABLE).body(e.getMessage());
    }
}
