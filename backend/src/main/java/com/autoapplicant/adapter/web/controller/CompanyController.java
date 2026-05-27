package com.autoapplicant.adapter.web.controller;

import com.autoapplicant.domain.company.Company;
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

    public CompanyController(GetCompaniesUseCase companies) {
        this.companies = companies;
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
