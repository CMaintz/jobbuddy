package com.autoapplicant.adapter.security;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.springframework.http.HttpStatus;
import org.springframework.mock.web.MockFilterChain;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;

class AiRateLimitFilterTest {

    private static final int ONE_REQUEST = 1;
    private static final int WINDOW_SECONDS = 60;

    private AiRateLimitFilter filter;

    @BeforeEach
    void authenticate() {
        filter = new AiRateLimitFilter(ONE_REQUEST, WINDOW_SECONDS);
        SecurityContextHolder.getContext().setAuthentication(
                new UsernamePasswordAuthenticationToken(UUID.randomUUID(), null, List.of()));
    }

    @AfterEach
    void clearAuthentication() {
        SecurityContextHolder.clearContext();
    }

    @ParameterizedTest
    @CsvSource({
            "POST, /api/v1/ai/generate-document",
            "POST, /api/v1/ai/cv/generate-structured",
            "POST, /api/v1/ai/parse-cv",
            "POST, /api/v1/ai/refine",
            "POST, /api/v1/profile/import/cv-pdf",
            "POST, /api/v1/users/me/import/linkedin-pdf",
            "POST, /api/v1/users/me/writing-style/analyze",
            "POST, /api/v1/jobs/3f2b8c1e-0000-4000-8000-000000000001/interview-prep/generate",
            "POST, /api/v1/jobs/3f2b8c1e-0000-4000-8000-000000000001/interview-prep/pack",
            "POST, /api/v1/jobs/3f2b8c1e-0000-4000-8000-000000000001/interview-prep/roleplay",
            "GET, /api/v1/skills/evidence-gaps/tailored",
            "POST, /api/v1/skills/evidence/draft",
            "GET, /api/v1/jobs/search/semantic",
            "POST, /api/v1/admin/linkedin/plan/refresh",
            "POST, /api/v1/admin/linkedin/run"
    })
    void an_ai_endpoint_is_limited_once_the_budget_is_spent(String method, String path)
            throws Exception {
        assertThat(send(method, path)).isEqualTo(HttpStatus.OK.value());
        assertThat(send(method, path)).isEqualTo(HttpStatus.TOO_MANY_REQUESTS.value());
    }

    @ParameterizedTest
    @CsvSource({
            "GET, /api/v1/jobs/recommendations",
            "POST, /api/v1/companies/outreach",
            "POST, /api/v1/jobs/3f2b8c1e-0000-4000-8000-000000000001/interview-prep",
            "GET, /api/v1/skills/evidence-gaps",
            "GET, /api/v1/ai/usage"
    })
    void a_non_ai_endpoint_is_never_limited(String method, String path) throws Exception {
        assertThat(send(method, path)).isEqualTo(HttpStatus.OK.value());
        assertThat(send(method, path)).isEqualTo(HttpStatus.OK.value());
    }

    @Test
    void ai_endpoints_share_one_budget() throws Exception {
        assertThat(send("POST", "/api/v1/ai/refine")).isEqualTo(HttpStatus.OK.value());
        assertThat(send("POST", "/api/v1/jobs/3f2b8c1e-0000-4000-8000-000000000001/interview-prep/pack"))
                .isEqualTo(HttpStatus.TOO_MANY_REQUESTS.value());
    }

    private int send(String method, String path) throws Exception {
        MockHttpServletRequest request = new MockHttpServletRequest(method, path);
        MockHttpServletResponse response = new MockHttpServletResponse();
        filter.doFilter(request, response, new MockFilterChain());
        return response.getStatus();
    }
}
