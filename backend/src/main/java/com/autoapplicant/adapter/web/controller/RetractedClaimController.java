package com.autoapplicant.adapter.web.controller;

import com.autoapplicant.adapter.security.SecurityContextHelper;
import com.autoapplicant.adapter.web.dto.user.RetractedClaimRequest;
import com.autoapplicant.domain.user.RetractedClaim;
import com.autoapplicant.port.in.user.ManageRetractedClaimsUseCase;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/profile/retracted-claims")
@Tag(name = "Retracted Claims")
public class RetractedClaimController {

    private final ManageRetractedClaimsUseCase useCase;
    private final SecurityContextHelper secCtx;

    public RetractedClaimController(ManageRetractedClaimsUseCase useCase, SecurityContextHelper secCtx) {
        this.useCase = useCase;
        this.secCtx = secCtx;
    }

    @Operation(summary = "List the current user's retracted (disowned) claims")
    @GetMapping
    public ResponseEntity<List<RetractedClaim>> list() {
        return ResponseEntity.ok(useCase.list(secCtx.getCurrentUserId()));
    }

    @Operation(summary = "Retract (disown) a claim so it never resurfaces in generated content")
    @PostMapping
    public ResponseEntity<RetractedClaim> add(@Valid @RequestBody RetractedClaimRequest req) {
        return ResponseEntity.ok(useCase.add(secCtx.getCurrentUserId(), req.claim(), req.reason()));
    }

    @Operation(summary = "Remove a retracted claim")
    @DeleteMapping("/{id}")
    public ResponseEntity<Void> remove(@PathVariable UUID id) {
        useCase.remove(secCtx.getCurrentUserId(), id);
        return ResponseEntity.noContent().build();
    }
}
