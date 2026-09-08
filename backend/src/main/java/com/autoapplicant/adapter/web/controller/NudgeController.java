package com.autoapplicant.adapter.web.controller;

import com.autoapplicant.adapter.security.SecurityContextHelper;
import com.autoapplicant.domain.notification.Nudge;
import com.autoapplicant.port.in.notification.GetNudgesUseCase;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/v1/users/me/nudges")
@Tag(name = "Nudges")
public class NudgeController {

    private final GetNudgesUseCase nudgesUseCase;
    private final SecurityContextHelper secCtx;

    public NudgeController(GetNudgesUseCase nudgesUseCase, SecurityContextHelper secCtx) {
        this.nudgesUseCase = nudgesUseCase;
        this.secCtx = secCtx;
    }

    @Operation(summary = "Computed deadline and follow-up nudges for the user's pipeline")
    @GetMapping
    public ResponseEntity<List<Nudge>> getNudges() {
        return ResponseEntity.ok(nudgesUseCase.getNudges(secCtx.getCurrentUserId()));
    }
}
