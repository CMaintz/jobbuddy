package com.autoapplicant.adapter.web.controller;

import com.autoapplicant.adapter.security.SecurityContextHelper;
import com.autoapplicant.adapter.web.dto.user.CareerTargetRequest;
import com.autoapplicant.domain.user.CareerTarget;
import com.autoapplicant.port.in.user.ManageCareerTargetUseCase;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/profile/career-target")
@Tag(name = "Career Target")
public class CareerTargetController {

    private final ManageCareerTargetUseCase useCase;
    private final SecurityContextHelper secCtx;

    public CareerTargetController(ManageCareerTargetUseCase useCase, SecurityContextHelper secCtx) {
        this.useCase = useCase;
        this.secCtx = secCtx;
    }

    @Operation(summary = "Get the current user's career target (archetypes, North-Star, narrative)")
    @GetMapping
    public ResponseEntity<CareerTarget> get() {
        return ResponseEntity.ok(useCase.get(secCtx.getCurrentUserId()));
    }

    @Operation(summary = "Create or update the current user's career target")
    @PutMapping
    public ResponseEntity<CareerTarget> upsert(@Valid @RequestBody CareerTargetRequest req) {
        return ResponseEntity.ok(useCase.upsert(secCtx.getCurrentUserId(),
                req.targetArchetypes(), req.northStar(), req.narrative(), req.cultureRequirements(),
                req.careerStage()));
    }
}
