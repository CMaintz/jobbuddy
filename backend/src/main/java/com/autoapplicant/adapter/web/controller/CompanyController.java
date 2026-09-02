package com.autoapplicant.adapter.web.controller;

import com.autoapplicant.adapter.security.SecurityContextHelper;
import com.autoapplicant.adapter.web.dto.company.TrackOutreachRequest;
import com.autoapplicant.adapter.web.dto.company.UpdateOutreachRequest;
import com.autoapplicant.domain.company.Company;
import com.autoapplicant.domain.job.Job;
import com.autoapplicant.domain.company.OutreachContact;
import com.autoapplicant.domain.company.OutreachTarget;
import com.autoapplicant.port.in.company.FindOutreachTargetsUseCase;
import com.autoapplicant.port.in.company.GetCompaniesUseCase;
import com.autoapplicant.port.in.company.ManageOutreachUseCase;
import com.autoapplicant.port.in.job.GetJobsByCompanyUseCase;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

@RestController
@Tag(name = "Companies")
public class CompanyController {

    private final GetCompaniesUseCase companies;
    private final FindOutreachTargetsUseCase outreachTargets;
    private final ManageOutreachUseCase outreach;
    private final GetJobsByCompanyUseCase companyJobs;
    private final SecurityContextHelper secCtx;

    public CompanyController(GetCompaniesUseCase companies,
                             FindOutreachTargetsUseCase outreachTargets,
                             ManageOutreachUseCase outreach,
                             GetJobsByCompanyUseCase companyJobs,
                             SecurityContextHelper secCtx) {
        this.companies = companies;
        this.outreachTargets = outreachTargets;
        this.outreach = outreach;
        this.companyJobs = companyJobs;
        this.secCtx = secCtx;
    }

    @Operation(summary = "Tracked unsolicited outreach, follow-ups due first")
    @GetMapping("/api/v1/companies/outreach")
    public ResponseEntity<List<OutreachContact>> listOutreach() {
        return ResponseEntity.ok(outreach.list(secCtx.getCurrentUserId()));
    }

    @Operation(summary = "Start tracking a company as an outreach target",
            description = "Idempotent per company: tracking one already tracked returns the "
                    + "existing record rather than starting a second thread of contact.")
    @PostMapping("/api/v1/companies/outreach")
    public ResponseEntity<OutreachContact> trackOutreach(@RequestBody TrackOutreachRequest req) {
        return ResponseEntity.ok(outreach.track(secCtx.getCurrentUserId(),
                req.companyId(), req.companyName(), req.contactName()));
    }

    @Operation(summary = "Update a tracked outreach (status, channel, follow-up date, notes)",
            description = "Marking it CONTACTED stamps the time and schedules a follow-up when "
                    + "none was given. Null fields are left unchanged.")
    @ApiResponses(@ApiResponse(responseCode = "404", description = "Outreach not found"))
    @PatchMapping("/api/v1/companies/outreach/{id}")
    public ResponseEntity<OutreachContact> updateOutreach(@PathVariable UUID id,
                                                          @RequestBody UpdateOutreachRequest req) {
        return ResponseEntity.ok(outreach.update(secCtx.getCurrentUserId(), id,
                req.status(), req.channel(), req.followUpDue(), req.notes()));
    }

    @Operation(summary = "Stop tracking an outreach")
    @ApiResponses(@ApiResponse(responseCode = "404", description = "Outreach not found"))
    @DeleteMapping("/api/v1/companies/outreach/{id}")
    public ResponseEntity<Void> untrackOutreach(@PathVariable UUID id) {
        outreach.untrack(secCtx.getCurrentUserId(), id);
        return ResponseEntity.noContent().build();
    }

    @Operation(summary = "Companies worth an unsolicited application, best first",
            description = "Ranks companies by how often they hire for the candidate's skills, how "
                    + "recently, and whether they have nothing open right now — the window an "
                    + "unsolicited application exists for. Each target carries the reasons it was "
                    + "picked. Empty when the profile lists no skills or technologies to match on.")
    @GetMapping("/api/v1/companies/outreach-targets")
    public ResponseEntity<List<OutreachTarget>> outreachTargets(
            @RequestParam(defaultValue = "20") int limit,
            @RequestParam(defaultValue = "false") boolean includeHiringNow) {
        return ResponseEntity.ok(outreachTargets.findOutreachTargets(
                secCtx.getCurrentUserId(), limit, includeHiringNow));
    }

    @Operation(summary = "Search companies by name")
    @GetMapping("/api/v1/companies")
    public ResponseEntity<List<Company>> search(
            @RequestParam(defaultValue = "") String q,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        return ResponseEntity.ok(companies.searchCompanies(q, page, size));
    }

    @Operation(summary = "Get company by ID")
    @GetMapping("/api/v1/companies/{id}")
    public ResponseEntity<Company> getById(@PathVariable UUID id) {
        return companies.getCompanyById(id)
                .map(ResponseEntity::ok)
                .orElse(ResponseEntity.notFound().build());
    }

    @Operation(summary = "Every posting we hold from one company, live ones first")
    @GetMapping("/api/v1/companies/{id}/jobs")
    public ResponseEntity<List<Job>> jobsByCompany(@PathVariable UUID id) {
        return ResponseEntity.ok(companyJobs.getJobsByCompany(id));
    }

    @Operation(summary = "Get company by slug")
    @GetMapping("/api/v1/companies/by-slug/{slug}")
    public ResponseEntity<Company> getBySlug(@PathVariable String slug) {
        return companies.getCompanyBySlug(slug)
                .map(ResponseEntity::ok)
                .orElse(ResponseEntity.notFound().build());
    }
}
