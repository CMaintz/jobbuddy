package com.autoapplicant.adapter.search;

import com.autoapplicant.config.AppProperties;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.http.*;
import org.springframework.stereotype.Component;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.RestTemplate;

import java.util.List;
import java.util.Map;

@Component
public class TypesenseSchemaInitializer implements ApplicationRunner {

    private static final Logger log = LoggerFactory.getLogger(TypesenseSchemaInitializer.class);
    private static final String COLLECTION = "jobs";

    private final AppProperties props;
    private final RestTemplate restTemplate = new RestTemplate();

    public TypesenseSchemaInitializer(AppProperties props) {
        this.props = props;
    }

    @Override
    public void run(ApplicationArguments args) {
        try {
            String baseUrl = props.getTypesense().getProtocol() + "://"
                    + props.getTypesense().getHost() + ":" + props.getTypesense().getPort();
            HttpHeaders headers = new HttpHeaders();
            headers.set("X-TYPESENSE-API-KEY", props.getTypesense().getApiKey());
            headers.setContentType(MediaType.APPLICATION_JSON);

            // Check if collection already exists
            try {
                restTemplate.exchange(baseUrl + "/collections/" + COLLECTION,
                        HttpMethod.GET, new HttpEntity<>(headers), Map.class);
                log.info("Typesense collection '{}' already exists", COLLECTION);
                return;
            } catch (HttpClientErrorException.NotFound ignored) {
                // collection doesn't exist, create it
            }

            Map<String, Object> schema = Map.of(
                    "name", COLLECTION,
                    "fields", List.of(
                            Map.of("name", "id", "type", "string"),
                            Map.of("name", "title", "type", "string"),
                            Map.of("name", "company_name", "type", "string", "facet", true),
                            Map.of("name", "description_clean", "type", "string"),
                            Map.of("name", "technologies", "type", "string[]", "facet", true),
                            Map.of("name", "skills", "type", "string[]", "facet", true),
                            Map.of("name", "seniority", "type", "string", "facet", true),
                            Map.of("name", "remote_type", "type", "string", "facet", true),
                            Map.of("name", "employment_type", "type", "string", "facet", true),
                            Map.of("name", "location", "type", "string"),
                            Map.of("name", "municipality", "type", "string", "facet", true),
                            Map.of("name", "salary_min", "type", "int32"),
                            Map.of("name", "salary_max", "type", "int32"),
                            Map.of("name", "posted_at", "type", "int64"),
                            Map.of("name", "url", "type", "string"),
                            Map.of("name", "source", "type", "string", "facet", true)
                    ),
                    "default_sorting_field", "posted_at"
            );

            restTemplate.exchange(baseUrl + "/collections",
                    HttpMethod.POST, new HttpEntity<>(schema, headers), Map.class);
            log.info("Created Typesense collection '{}'", COLLECTION);
        } catch (Exception e) {
            log.warn("Could not initialize Typesense schema: {}", e.getMessage());
        }
    }
}
