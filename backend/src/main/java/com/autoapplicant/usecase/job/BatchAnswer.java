package com.autoapplicant.usecase.job;

import com.autoapplicant.domain.job.Job;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Reads a batch answer ({@code {"postings": [{"id": "P1", ...}, ...]}}) back into one enrichment
 * per posting. Items are matched by the id issued for the call, never by position, and an id
 * given twice is trusted for neither copy. Each item is parsed on its own by the single-posting
 * parser, so its line ranges are bounded by that posting's own text.
 */
final class BatchAnswer {

    private final ObjectMapper json;
    private final JobEnrichmentService parser;

    BatchAnswer(ObjectMapper json, JobEnrichmentService parser) {
        this.json = json;
        this.parser = parser;
    }

    /**
     * The postings, by issued id, whose item came back once and parsed into a summary. An
     * answer that does not parse yields none; the caller retries those postings like any it
     * missed. A provider that fails outright throws before this is reached.
     */
    Map<String, Job> validItems(String answer, Map<String, Job> byId) {
        List<Map<String, Object>> items = items(answer);
        Map<String, Integer> timesGiven = timesEachIdIsGiven(items);
        Map<String, Job> valid = new HashMap<>();
        for (Map<String, Object> item : items) {
            if (item.get("id") instanceof String id && byId.containsKey(id) && timesGiven.get(id) == 1) {
                Job done = parse(byId.get(id), item);
                if (done.aiSummary() != null) {
                    valid.put(id, done);
                }
            }
        }
        return valid;
    }

    private static Map<String, Integer> timesEachIdIsGiven(List<Map<String, Object>> items) {
        Map<String, Integer> times = new HashMap<>();
        for (Map<String, Object> item : items) {
            if (item.get("id") instanceof String id) {
                times.merge(id, 1, Integer::sum);
            }
        }
        return times;
    }

    /** The answer's items; empty when it does not parse or holds no postings list. */
    private List<Map<String, Object>> items(String answer) {
        try {
            Map<String, Object> root = json.readValue(
                    JobEnrichmentService.sanitizeJsonResponse(answer), new TypeReference<>() {});
            if (!(root.get("postings") instanceof List<?> list)) {
                return List.of();
            }
            List<Map<String, Object>> items = new ArrayList<>();
            for (Object entry : list) {
                if (entry instanceof Map<?, ?> map) {
                    Map<String, Object> item = new LinkedHashMap<>();
                    map.forEach((key, value) -> item.put(String.valueOf(key), value));
                    items.add(item);
                }
            }
            return items;
        } catch (JsonProcessingException e) {
            return List.of();
        }
    }

    private Job parse(Job job, Map<String, Object> item) {
        try {
            return parser.applyEnrichment(job, json.writeValueAsString(item));
        } catch (JsonProcessingException e) {
            return job;
        }
    }
}
