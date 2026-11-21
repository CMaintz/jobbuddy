package com.autoapplicant.config;

import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.asyncDispatch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.options;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.request;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.autoapplicant.adapter.security.SecurityContextHelper;
import com.autoapplicant.adapter.web.controller.AiController;
import com.autoapplicant.adapter.web.controller.AuthController;
import com.autoapplicant.domain.ai.SkillGapReport;
import com.autoapplicant.domain.user.User;
import com.autoapplicant.domain.user.UserRole;
import com.autoapplicant.port.in.ai.AnalyzeCvUseCase;
import com.autoapplicant.port.in.ai.AnalyzeSkillGapsUseCase;
import com.autoapplicant.port.in.ai.GenerateDocumentUseCase;
import com.autoapplicant.port.in.ai.GetAiUsageUseCase;
import com.autoapplicant.port.in.ai.ManageAiCredentialUseCase;
import com.autoapplicant.port.in.ai.RefineDocumentUseCase;
import com.autoapplicant.port.in.ai.ReviewDocumentUseCase;
import com.autoapplicant.port.in.auth.ProvisionLocalUserUseCase;
import com.autoapplicant.port.in.document.GenerateTailoredCvUseCase;
import com.autoapplicant.port.in.document.GetCvRenderModelUseCase;
import com.autoapplicant.port.in.document.ParseCvUseCase;
import com.autoapplicant.port.in.document.PersistGeneratedDocumentUseCase;
import com.autoapplicant.port.in.user.CompleteOnboardingUseCase;
import com.autoapplicant.port.in.user.GetUserProfileUseCase;
import com.autoapplicant.port.out.security.SecretCipherPort;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executor;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import org.springframework.test.web.servlet.request.RequestPostProcessor;

/**
 * The desktop profile's filter chain, run for real: only the shell, on this machine, holding this
 * launch's token, gets in. Everything behind the controllers is mocked.
 */
@WebMvcTest(
        controllers = {AuthController.class, AiController.class},
        properties = {
            "app.desktop.token=" + DesktopSecurityTest.TOKEN,
            "app.desktop.shell-origin=" + DesktopSecurityTest.SHELL_ORIGIN,
            // The profile's default is the user's real local app data.
            "app.data-dir=build/desktop-test-data"
        })
@ActiveProfiles("desktop")
@Import({SecurityConfig.class, SecurityContextHelper.class})
class DesktopSecurityTest {

    static final String TOKEN = "per-launch-token-0123456789abcdef0123456789";
    static final String SHELL_ORIGIN = "app://jobbuddy";
    private static final String TOKEN_HEADER = "X-JobBuddy-Token";
    private static final String ME = "/api/v1/auth/me";
    private static final String SKILL_GAPS = "/api/v1/ai/skill-gaps";

    @Autowired MockMvc mvc;

    @MockitoBean ProvisionLocalUserUseCase localUser;
    @MockitoBean GetUserProfileUseCase userProfile;
    @MockitoBean CompleteOnboardingUseCase completeOnboarding;

    @MockitoBean AnalyzeCvUseCase analyze;
    @MockitoBean ParseCvUseCase parseCv;
    @MockitoBean RefineDocumentUseCase refine;
    @MockitoBean ReviewDocumentUseCase reviewDocument;
    @MockitoBean AnalyzeSkillGapsUseCase skillGaps;
    @MockitoBean GenerateDocumentUseCase generateDocument;
    @MockitoBean GetCvRenderModelUseCase cvRenderModel;
    @MockitoBean GenerateTailoredCvUseCase generateTailoredCv;
    @MockitoBean PersistGeneratedDocumentUseCase persistedDocuments;
    @MockitoBean GetAiUsageUseCase aiUsage;
    @MockitoBean ManageAiCredentialUseCase aiCredentials;
    @MockitoBean SecretCipherPort secretCipher;
    @MockitoBean(name = "userAiTaskExecutor") Executor userAiExecutor;

    private final User local = new User(UUID.randomUUID(), "local@jobbuddy.invalid", null, null,
            null, UserRole.USER, true, true, Instant.now(), Instant.now());

    @BeforeEach
    void givenTheLocalUser() {
        when(localUser.provisionLocalUser()).thenReturn(local);
        when(userProfile.getUser(local.id())).thenReturn(Optional.of(local));
    }

    @Test
    void the_shell_token_from_loopback_is_the_local_user() throws Exception {
        mvc.perform(get(ME).header(TOKEN_HEADER, TOKEN))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.userId").value(local.id().toString()))
                .andExpect(jsonPath("$.role").value("USER"));
    }

    @Test
    void a_request_without_the_token_is_refused() throws Exception {
        mvc.perform(get(ME)).andExpect(status().isForbidden());

        verifyNoInteractions(userProfile);
    }

    @Test
    void a_wrong_token_is_refused() throws Exception {
        mvc.perform(get(ME).header(TOKEN_HEADER, TOKEN + "x")).andExpect(status().isForbidden());
        mvc.perform(get(ME).header(TOKEN_HEADER, "")).andExpect(status().isForbidden());

        verifyNoInteractions(userProfile);
    }

    @Test
    void the_right_token_from_another_machine_is_refused() throws Exception {
        mvc.perform(get(ME).header(TOKEN_HEADER, TOKEN).with(remoteAddress("192.168.1.20")))
                .andExpect(status().isForbidden());

        verifyNoInteractions(userProfile);
    }

    @Test
    void a_forwarded_header_cannot_pass_a_remote_caller_off_as_loopback() throws Exception {
        mvc.perform(get(ME)
                        .header(TOKEN_HEADER, TOKEN)
                        .header("X-Forwarded-For", "127.0.0.1")
                        .header("Forwarded", "for=127.0.0.1")
                        .with(remoteAddress("10.0.0.7")))
                .andExpect(status().isForbidden());

        verifyNoInteractions(userProfile);
    }

    @Test
    void the_local_user_is_not_an_admin() throws Exception {
        mvc.perform(post("/api/v1/admin/crawler/trigger").header(TOKEN_HEADER, TOKEN))
                .andExpect(status().isForbidden());
    }

    @Test
    void a_deferred_result_reaches_the_shell_once_the_work_completes() throws Exception {
        CompletableFuture<SkillGapReport> pending = new CompletableFuture<>();
        when(skillGaps.analyzeSkillGaps(local.id())).thenReturn(pending);

        MvcResult started = mvc.perform(post(SKILL_GAPS).header(TOKEN_HEADER, TOKEN))
                .andExpect(request().asyncStarted())
                .andReturn();
        pending.complete(new SkillGapReport(List.of(), "summary", 3));

        mvc.perform(asyncDispatch(started))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.jobsAnalyzed").value(3));
    }

    @Test
    void cors_admits_the_shell_origin_only() throws Exception {
        mvc.perform(preflight(SHELL_ORIGIN))
                .andExpect(status().isOk())
                .andExpect(header().string("Access-Control-Allow-Origin", SHELL_ORIGIN));
        mvc.perform(preflight("http://localhost:4200")).andExpect(status().isForbidden());
    }

    private static MockHttpServletRequestBuilder preflight(String origin) {
        return options(ME)
                .header("Origin", origin)
                .header("Access-Control-Request-Method", "GET")
                .header("Access-Control-Request-Headers", TOKEN_HEADER);
    }

    private static RequestPostProcessor remoteAddress(String address) {
        return request -> {
            request.setRemoteAddr(address);
            return request;
        };
    }
}
