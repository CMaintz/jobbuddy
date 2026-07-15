package com.autoapplicant.adapter.search;

import com.autoapplicant.config.AppProperties;
import com.autoapplicant.domain.job.Job;
import com.autoapplicant.domain.search.JobSearchFilters;
import com.autoapplicant.domain.search.JobSearchQuery;
import com.autoapplicant.domain.search.JobSearchResult;
import com.autoapplicant.port.out.job.JobSearchPort;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.*;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestTemplate;

import java.time.Instant;
import java.util.*;
import java.util.stream.Collectors;

@Component
public class TypesenseSearchAdapter implements JobSearchPort {

    private static final Logger log = LoggerFactory.getLogger(TypesenseSearchAdapter.class);
    private static final String COLLECTION = "jobs";

    private final RestTemplate restTemplate;
    private final AppProperties props;
    private final ObjectMapper objectMapper;

    public TypesenseSearchAdapter(AppProperties props, ObjectMapper objectMapper) {
        this.props = props;
        this.objectMapper = objectMapper;
        this.restTemplate = new RestTemplate();
    }

    private String baseUrl() {
        return props.getTypesense().getProtocol() + "://"
                + props.getTypesense().getHost() + ":"
                + props.getTypesense().getPort();
    }

    private HttpHeaders headers() {
        HttpHeaders h = new HttpHeaders();
        h.set("X-TYPESENSE-API-KEY", props.getTypesense().getApiKey());
        h.setContentType(MediaType.APPLICATION_JSON);
        return h;
    }

    @Override
    public void index(Job job) {
        try {
            Map<String, Object> doc = toDocument(job);
            String url = baseUrl() + "/collections/" + COLLECTION + "/documents?action=upsert";
            restTemplate.exchange(url, HttpMethod.POST, new HttpEntity<>(doc, headers()), String.class);
        } catch (Exception e) {
            log.warn("Failed to index job {} in Typesense: {}", job.id(), e.getMessage());
        }
    }

    @Override
    public void bulkIndex(List<Job> jobs) {
        jobs.forEach(this::index);
    }

    @Override
    public void delete(UUID jobId) {
        try {
            String url = baseUrl() + "/collections/" + COLLECTION + "/documents/" + jobId;
            restTemplate.exchange(url, HttpMethod.DELETE, new HttpEntity<>(headers()), String.class);
        } catch (Exception e) {
            log.warn("Failed to delete job {} from Typesense: {}", jobId, e.getMessage());
        }
    }

    @Override
    @SuppressWarnings("unchecked")
    public JobSearchResult search(JobSearchQuery query) {
        try {
            String q = (query.text() != null && !query.text().isBlank()) ? query.text() : "*";
            String filterBy = buildFilterBy(query.filters());
            String url = baseUrl() + "/collections/" + COLLECTION + "/documents/search"
                    + "?q=" + q
                    + "&query_by=title,description_clean,technologies,company_name"
                    + "&per_page=" + Math.min(query.size(), 250)
                    + "&page=" + (query.page() + 1)
                    + "&facet_by=technologies,seniority,remote_type,employment_type,municipality,job_category"
                    + (filterBy.isBlank() ? "" : "&filter_by=" + filterBy);

            ResponseEntity<Map> response = restTemplate.exchange(
                    url, HttpMethod.GET, new HttpEntity<>(headers()), Map.class);

            if (response.getBody() == null) return emptyResult(query);

            Map<String, Object> body = response.getBody();
            int total = (int) body.getOrDefault("found", 0);
            List<Map<String, Object>> hits = (List<Map<String, Object>>) body.getOrDefault("hits", List.of());

            List<Job> jobs = hits.stream()
                    .map(hit -> (Map<String, Object>) hit.get("document"))
                    .filter(Objects::nonNull)
                    .map(this::fromDocument)
                    .collect(Collectors.toList());

            return new JobSearchResult(jobs, total, query.page(), query.size(), Map.of());
        } catch (Exception e) {
            log.warn("Typesense search failed: {}", e.getMessage());
            return emptyResult(query);
        }
    }

    private String buildFilterBy(JobSearchFilters filters) {
        if (filters == null) return "";
        List<String> parts = new ArrayList<>();
        if (filters.remoteTypes() != null && !filters.remoteTypes().isEmpty()) {
            String vals = filters.remoteTypes().stream().map(Enum::name).collect(Collectors.joining(","));
            parts.add("remote_type:[" + vals + "]");
        }
        if (filters.salaryMin() != null) parts.add("salary_min:>=" + filters.salaryMin());
        if (filters.technologies() != null && !filters.technologies().isEmpty()) {
            String vals = String.join(",", filters.technologies());
            parts.add("technologies:[" + vals + "]");
        }
        if (filters.seniority() != null && !filters.seniority().isEmpty()) {
            String vals = filters.seniority().stream().map(Enum::name).collect(Collectors.joining(","));
            parts.add("seniority:[" + vals + "]");
        }
        if (filters.jobCategories() != null && !filters.jobCategories().isEmpty()) {
            String vals = String.join(",", filters.jobCategories());
            parts.add("job_category:[" + vals + "]");
        }
        return String.join(" && ", parts);
    }

    private Map<String, Object> toDocument(Job job) {
        Map<String, Object> doc = new LinkedHashMap<>();
        doc.put("id", job.id().toString());
        doc.put("title", job.title());
        doc.put("company_name", job.companyName() != null ? job.companyName() : "");
        doc.put("description_clean", job.descriptionClean() != null ? job.descriptionClean() : "");
        doc.put("technologies", job.technologies() != null ? job.technologies() : List.of());
        doc.put("skills", job.skills() != null ? job.skills() : List.of());
        doc.put("seniority", job.seniority() != null ? job.seniority().name() : "");
        doc.put("remote_type", job.remoteType() != null ? job.remoteType().name() : "");
        doc.put("employment_type", job.employmentType() != null ? job.employmentType().name() : "");
        doc.put("location", job.location() != null ? job.location() : "");
        doc.put("municipality", job.municipality() != null ? job.municipality() : "");
        doc.put("salary_min", job.salaryMin() != null ? job.salaryMin() : 0);
        doc.put("salary_max", job.salaryMax() != null ? job.salaryMax() : 0);
        doc.put("posted_at", job.postedAt() != null ? job.postedAt().getEpochSecond() : 0L);
        doc.put("url", job.url());
        doc.put("source", job.source() != null ? job.source().name() : "");
        if (job.jobCategory() != null) {
            doc.put("job_category", job.jobCategory().name());
        }
        return doc;
    }

    @SuppressWarnings("unchecked")
    private Job fromDocument(Map<String, Object> doc) {
        UUID id = doc.get("id") != null ? UUID.fromString((String) doc.get("id")) : null;
        String rawCategory = (String) doc.get("job_category");
        com.autoapplicant.domain.job.JobCategory jobCategory = null;
        if (rawCategory != null) {
            try { jobCategory = com.autoapplicant.domain.job.JobCategory.valueOf(rawCategory); }
            catch (IllegalArgumentException ignored) {}
        }
        return new Job(id, null, null,
                (String) doc.getOrDefault("url", ""),
                (String) doc.getOrDefault("title", ""),
                null, (String) doc.getOrDefault("company_name", ""),
                null, (String) doc.getOrDefault("description_clean", ""),
                null, null, null,
                (String) doc.getOrDefault("location", ""),
                (String) doc.getOrDefault("municipality", ""),
                null, null, null, null, null,
                (List<String>) doc.getOrDefault("technologies", List.of()),
                (List<String>) doc.getOrDefault("skills", List.of()),
                List.of(), null, null, null, List.of(), null, null, true, jobCategory, null, null, null, null, null);
    }

    private JobSearchResult emptyResult(JobSearchQuery query) {
        return new JobSearchResult(List.of(), 0, query.page(), query.size(), Map.of());
    }
}
