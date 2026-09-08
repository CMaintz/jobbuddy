package com.autoapplicant.adapter.web.controller;

import com.autoapplicant.adapter.security.SecurityContextHelper;
import com.autoapplicant.domain.document.RecordedQualityScore;
import com.autoapplicant.port.in.document.GetQualityTrendUseCase;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/v1/documents/quality-scores")
@Tag(name = "Document Quality")
public class QualityScoreController {

    private final GetQualityTrendUseCase qualityTrend;
    private final SecurityContextHelper secCtx;

    public QualityScoreController(GetQualityTrendUseCase qualityTrend, SecurityContextHelper secCtx) {
        this.qualityTrend = qualityTrend;
        this.secCtx = secCtx;
    }

    @Operation(summary = "Recent document quality scores, newest first",
            description = "Deterministic scores recorded when each letter was generated: filler, "
                    + "evidence, unsupported figures, keyword coverage, CV echo and length drift. "
                    + "Comparable across generations, so a prompt or profile change shows up as a "
                    + "trend rather than an impression.")
    @GetMapping
    public ResponseEntity<List<RecordedQualityScore>> recent(@RequestParam(defaultValue = "20") int limit) {
        return ResponseEntity.ok(qualityTrend.recentScores(secCtx.getCurrentUserId(), limit));
    }
}
