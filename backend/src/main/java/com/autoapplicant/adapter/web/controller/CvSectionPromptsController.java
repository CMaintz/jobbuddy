package com.autoapplicant.adapter.web.controller;

import com.autoapplicant.adapter.security.SecurityContextHelper;
import com.autoapplicant.adapter.web.dto.document.CvSectionPromptsResponse;
import com.autoapplicant.adapter.web.dto.document.SaveCvSectionPromptsRequest;
import com.autoapplicant.domain.document.CvSection;
import com.autoapplicant.domain.document.CvSectionPrompts;
import com.autoapplicant.port.in.document.ManageCvSectionPromptsUseCase;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.EnumMap;
import java.util.Map;

@RestController
@RequestMapping("/api/v1/users/me/cv-section-prompts")
@Tag(name = "CV Section Prompts")
public class CvSectionPromptsController {

    private final ManageCvSectionPromptsUseCase useCase;
    private final SecurityContextHelper secCtx;

    public CvSectionPromptsController(ManageCvSectionPromptsUseCase useCase, SecurityContextHelper secCtx) {
        this.useCase = useCase;
        this.secCtx = secCtx;
    }

    @Operation(summary = "The user's saved per-section CV tailoring prompts")
    @GetMapping
    public ResponseEntity<CvSectionPromptsResponse> get() {
        return ResponseEntity.ok(CvSectionPromptsResponse.from(useCase.get(secCtx.getCurrentUserId())));
    }

    @Operation(summary = "Save the user's per-section CV tailoring prompts",
            description = "A blank or omitted section value clears that section. Unknown keys are ignored.")
    @PutMapping
    public ResponseEntity<CvSectionPromptsResponse> save(@RequestBody SaveCvSectionPromptsRequest req) {
        CvSectionPrompts saved = useCase.save(secCtx.getCurrentUserId(), toDomain(req.prompts()));
        return ResponseEntity.ok(CvSectionPromptsResponse.from(saved));
    }

    private static Map<CvSection, String> toDomain(Map<String, String> wire) {
        Map<CvSection, String> out = new EnumMap<>(CvSection.class);
        if (wire != null) {
            wire.forEach((key, value) -> CvSection.fromKey(key).ifPresent(section -> out.put(section, value)));
        }
        return out;
    }
}
