package com.autoapplicant.adapter.web.controller;

import com.autoapplicant.adapter.security.SecurityContextHelper;
import com.autoapplicant.adapter.web.dto.document.CvUploadRequest;
import com.autoapplicant.domain.document.CvVersion;
import com.autoapplicant.port.in.document.GetCvVersionsUseCase;
import com.autoapplicant.port.in.document.UploadCvUseCase;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/v1/cv")
@Tag(name = "CV")
public class DocumentController {

    private final UploadCvUseCase upload;
    private final GetCvVersionsUseCase getVersions;
    private final SecurityContextHelper secCtx;

    public DocumentController(UploadCvUseCase upload, GetCvVersionsUseCase getVersions,
                               SecurityContextHelper secCtx) {
        this.upload = upload;
        this.getVersions = getVersions;
        this.secCtx = secCtx;
    }

    @GetMapping
    public ResponseEntity<List<CvVersion>> list() {
        return ResponseEntity.ok(getVersions.getCvVersions(secCtx.getCurrentUserId()));
    }

    @PostMapping
    public ResponseEntity<CvVersion> upload(@Valid @RequestBody CvUploadRequest req) {
        CvVersion cv = upload.uploadCv(secCtx.getCurrentUserId(), req.name(), req.content(), req.format());
        return ResponseEntity.ok(cv);
    }
}
