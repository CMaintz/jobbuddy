package com.autoapplicant.config;

import static org.hamcrest.Matchers.not;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.autoapplicant.adapter.web.controller.CrawlerAdminController;
import com.autoapplicant.domain.user.User;
import com.autoapplicant.domain.user.UserRole;
import com.autoapplicant.port.in.auth.ProvisionFirebaseUserUseCase;
import com.autoapplicant.port.in.crawler.TriggerCrawlUseCase;
import com.autoapplicant.port.out.crawler.CrawlerStateRepositoryPort;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseToken;
import java.time.Instant;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.HttpStatus;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

/**
 * Runs requests through the real security chain and the real FirebaseTokenFilter, with only
 * Firebase token verification and the user lookup mocked.
 *
 * <p>An anonymous request gets 403, not 401: SecurityConfig sets no authentication entry point,
 * so Spring Security falls back to Http403ForbiddenEntryPoint.
 */
@WebMvcTest(
        controllers = CrawlerAdminController.class,
        properties = "app.cors.allowed-origins=http://localhost:4200")
@Import(SecurityConfig.class)
class AdminAccessSecurityTest {

    private static final String USER_TOKEN = "user-token";
    private static final String ADMIN_TOKEN = "admin-token";
    private static final String CRAWLER_STATUS = "/api/v1/admin/crawler/status";

    @Autowired MockMvc mvc;

    @MockitoBean FirebaseAuth firebaseAuth;
    @MockitoBean ProvisionFirebaseUserUseCase provisionUser;
    @MockitoBean TriggerCrawlUseCase triggerCrawl;
    @MockitoBean CrawlerStateRepositoryPort crawlerStateRepo;

    @Test
    void a_request_without_a_token_is_rejected_on_a_protected_endpoint() throws Exception {
        mvc.perform(get("/api/v1/applications")).andExpect(status().isForbidden());
    }

    @Test
    void an_admin_token_reaches_an_admin_endpoint() throws Exception {
        givenToken(ADMIN_TOKEN, UserRole.ADMIN);

        mvc.perform(get(CRAWLER_STATUS).header("Authorization", "Bearer " + ADMIN_TOKEN))
                .andExpect(status().isOk());
    }

    @Test
    void a_user_token_cannot_trigger_a_crawl() throws Exception {
        givenToken(USER_TOKEN, UserRole.USER);

        mvc.perform(post("/api/v1/admin/crawler/trigger")
                        .header("Authorization", "Bearer " + USER_TOKEN))
                .andExpect(status().isForbidden());

        verifyNoInteractions(triggerCrawl);
    }

    // The admin controllers also carry @PreAuthorize, so a mapped endpoint stays 403 even if the
    // URL rule goes. A path with no handler is decided by the /api/v1/admin/** rule alone:
    // without it the request is authenticated and dispatched, and comes back 404.
    @Test
    void the_admin_url_rule_alone_forbids_a_user_token() throws Exception {
        givenToken(USER_TOKEN, UserRole.USER);

        mvc.perform(get("/api/v1/admin/no-such-endpoint")
                        .header("Authorization", "Bearer " + USER_TOKEN))
                .andExpect(status().isForbidden());
    }

    // No Swagger handler exists in this slice, so only the security verdict is observable.
    @Test
    void swagger_ui_is_reachable_without_a_token() throws Exception {
        mvc.perform(get("/swagger-ui.html"))
                .andExpect(status().is(not(HttpStatus.FORBIDDEN.value())));
    }

    private void givenToken(String idToken, UserRole dbRole) throws Exception {
        String firebaseUid = "uid-" + idToken;
        String email = idToken + "@example.com";
        FirebaseToken token = mock(FirebaseToken.class);
        when(token.isEmailVerified()).thenReturn(true);
        when(token.getUid()).thenReturn(firebaseUid);
        when(token.getEmail()).thenReturn(email);
        when(firebaseAuth.verifyIdToken(idToken)).thenReturn(token);
        User user = new User(UUID.randomUUID(), email, null, null, firebaseUid, dbRole,
                true, true, Instant.now(), Instant.now());
        when(provisionUser.findOrCreate(firebaseUid, email, null)).thenReturn(user);
    }
}
