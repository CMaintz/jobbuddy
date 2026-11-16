package com.autoapplicant.config;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.asyncDispatch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.request;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.autoapplicant.adapter.security.SecurityContextHelper;
import com.autoapplicant.adapter.web.controller.AiController;
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
import com.autoapplicant.port.in.auth.ProvisionFirebaseUserUseCase;
import com.autoapplicant.port.in.document.GenerateTailoredCvUseCase;
import com.autoapplicant.port.in.document.GetCvRenderModelUseCase;
import com.autoapplicant.port.in.document.ParseCvUseCase;
import com.autoapplicant.port.in.document.PersistGeneratedDocumentUseCase;
import com.autoapplicant.port.out.security.SecretCipherPort;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseAuthException;
import com.google.firebase.auth.FirebaseToken;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executor;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

/**
 * The AI endpoints answer with a DeferredResult, so the response is written on a second, ASYNC
 * dispatch through the security chain. That dispatch carries no Authorization check of its own:
 * it has to find the user the REQUEST dispatch authenticated, or it is denied after the work is
 * already done. Runs the real SecurityConfig and FirebaseTokenFilter; only Firebase token
 * verification and the user lookup are mocked.
 */
@WebMvcTest(
        controllers = AiController.class,
        properties = "app.cors.allowed-origins=http://localhost:4200")
@Import({SecurityConfig.class, SecurityContextHelper.class})
class AsyncDispatchSecurityTest {

    private static final String TOKEN = "user-token";
    private static final String SKILL_GAPS = "/api/v1/ai/skill-gaps";
    private static final String GENERATE_DOCUMENT = "/api/v1/ai/generate-document";

    @Autowired MockMvc mvc;

    @MockitoBean FirebaseAuth firebaseAuth;
    @MockitoBean ProvisionFirebaseUserUseCase provisionUser;

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

    @Test
    void a_signed_in_user_gets_the_result_once_the_deferred_work_completes() throws Exception {
        UUID userId = givenValidToken();
        CompletableFuture<SkillGapReport> pending = new CompletableFuture<>();
        when(skillGaps.analyzeSkillGaps(userId)).thenReturn(pending);

        MvcResult started = mvc.perform(post(SKILL_GAPS).header("Authorization", "Bearer " + TOKEN))
                .andExpect(request().asyncStarted())
                .andReturn();
        pending.complete(new SkillGapReport(List.of(), "summary", 3));

        mvc.perform(asyncDispatch(started))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.jobsAnalyzed").value(3));
    }

    @Test
    void an_anonymous_caller_never_starts_deferred_generation() throws Exception {
        mvc.perform(post(GENERATE_DOCUMENT)
                        .contentType("application/json")
                        .content("{\"documentType\":\"COVER_LETTER\"}"))
                .andExpect(status().isForbidden())
                .andExpect(request().asyncNotStarted());

        verifyNoInteractions(generateDocument);
    }

    @Test
    void a_token_firebase_rejects_never_starts_deferred_work() throws Exception {
        when(firebaseAuth.verifyIdToken(TOKEN)).thenThrow(mock(FirebaseAuthException.class));

        mvc.perform(post(SKILL_GAPS).header("Authorization", "Bearer " + TOKEN))
                .andExpect(status().isForbidden())
                .andExpect(request().asyncNotStarted());

        verifyNoInteractions(skillGaps, provisionUser);
    }

    @Test
    void an_unverified_email_never_starts_deferred_work() throws Exception {
        givenValidToken();
        FirebaseToken unverified = firebaseAuth.verifyIdToken(TOKEN);
        when(unverified.isEmailVerified()).thenReturn(false);

        mvc.perform(post(SKILL_GAPS).header("Authorization", "Bearer " + TOKEN))
                .andExpect(status().isForbidden())
                .andExpect(request().asyncNotStarted());

        verifyNoInteractions(skillGaps);
    }

    private UUID givenValidToken() throws Exception {
        String firebaseUid = "uid-" + TOKEN;
        String email = TOKEN + "@example.com";
        FirebaseToken token = mock(FirebaseToken.class);
        when(token.isEmailVerified()).thenReturn(true);
        when(token.getUid()).thenReturn(firebaseUid);
        when(token.getEmail()).thenReturn(email);
        when(firebaseAuth.verifyIdToken(TOKEN)).thenReturn(token);
        User user = new User(UUID.randomUUID(), email, null, null, firebaseUid, UserRole.USER,
                true, true, Instant.now(), Instant.now());
        when(provisionUser.findOrCreate(any(), any(), any())).thenReturn(user);
        return user.id();
    }
}
