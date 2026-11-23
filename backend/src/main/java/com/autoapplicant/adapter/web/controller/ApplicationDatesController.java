package com.autoapplicant.adapter.web.controller;

import com.autoapplicant.adapter.security.SecurityContextHelper;
import com.autoapplicant.adapter.web.dto.application.ApplicationResponse;
import com.autoapplicant.adapter.web.dto.application.UpdateApplicationDatesRequest;
import com.autoapplicant.domain.application.Application;
import com.autoapplicant.domain.job.Job;
import com.autoapplicant.port.in.application.UpdateApplicationDatesUseCase;
import com.autoapplicant.port.in.job.GetJobByIdUseCase;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;
import java.util.UUID;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** The user's own deadline and interview time on an application. */
@RestController
@RequestMapping("/api/v1/applications/{id}/dates")
@Tag(name = "Applications")
public class ApplicationDatesController {

    private final UpdateApplicationDatesUseCase updateDates;
    private final GetJobByIdUseCase getJobById;
    private final SecurityContextHelper secCtx;

    public ApplicationDatesController(UpdateApplicationDatesUseCase updateDates,
                                      GetJobByIdUseCase getJobById,
                                      SecurityContextHelper secCtx) {
        this.updateDates = updateDates;
        this.getJobById = getJobById;
        this.secCtx = secCtx;
    }

    @Operation(summary = "Set the application's own deadline and interview time; an omitted date is cleared")
    @ApiResponse(responseCode = "400", description = "Application not found")
    @PutMapping
    public ResponseEntity<ApplicationResponse> update(@PathVariable UUID id,
                                                      @RequestBody UpdateApplicationDatesRequest req) {
        Application updated = updateDates.updateDates(id, secCtx.getCurrentUserId(),
                req.deadline(), req.interviewAt());
        Job job = updated.jobId() != null ? getJobById.getJobById(updated.jobId()).orElse(null) : null;
        return ResponseEntity.ok(ApplicationResponse.from(updated, job));
    }
}
