package com.autoapplicant.adapter.web.controller;

import com.autoapplicant.adapter.security.SecurityContextHelper;
import com.autoapplicant.domain.company.Company;
import com.autoapplicant.domain.company.OutreachTarget;
import com.autoapplicant.port.in.company.FindOutreachTargetsUseCase;
import com.autoapplicant.port.in.company.GetCompaniesUseCase;
import io.swagger.v3.oas.annotations.Operation;
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
    private final SecurityContextHelper secCtx;

    public CompanyController(GetCompaniesUseCase companies,
                             FindOutreachTargetsUseCase outreachTargets,
                             SecurityContextHelper secCtx) {
        this.companies = companies;
        this.outreachTargets = outreachTargets;
        this.secCtx = secCtx;
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

    @Operation(summary = "Get company by slug")
    @GetMapping("/api/v1/companies/by-slug/{slug}")
    public ResponseEntity<Company> getBySlug(@PathVariable String slug) {
        return companies.getCompanyBySlug(slug)
                .map(ResponseEntity::ok)
                .orElse(ResponseEntity.notFound().build());
    }
}
