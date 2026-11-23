package com.autoapplicant.adapter.web.controller;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.request;

import com.autoapplicant.adapter.security.SecurityContextHelper;
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
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executor;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

/**
 * A document through the CLI provider is two sequential agent calls of up to the CLI timeout
 * each. Any AI endpoint that keeps its own shorter deadline answers 504 while the work runs on,
 * so every one of them has to take its deadline from the shared setting.
 */
@WebMvcTest(controllers = AiController.class, properties = "app.ai.request-timeout-seconds=432")
@AutoConfigureMockMvc(addFilters = false)
class AiControllerRequestTimeoutTest {

    private static final long CONFIGURED_TIMEOUT_MS = 432_000L;

    @Autowired MockMvc mvc;

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
    @MockitoBean SecurityContextHelper secCtx;
    @MockitoBean ProvisionFirebaseUserUseCase provisionUser;
    @MockitoBean FirebaseAuth firebaseAuth;

    @BeforeEach
    void setUp() {
        when(secCtx.getCurrentUserId()).thenReturn(UUID.randomUUID());
        when(generateDocument.generateDocument(any())).thenReturn(new CompletableFuture<>());
        when(refine.refine(any())).thenReturn(new CompletableFuture<>());
        when(reviewDocument.review(any())).thenReturn(new CompletableFuture<>());
        when(skillGaps.analyzeSkillGaps(any())).thenReturn(new CompletableFuture<>());
        when(analyze.analyze(any(), any(), any(), any())).thenReturn(new CompletableFuture<>());
    }

    @ParameterizedTest(name = "{0}")
    @CsvSource(delimiter = '|', value = {
        "/api/v1/ai/cv/generate-structured | {}",
        "/api/v1/ai/generate-document      | {\"documentType\":\"COVER_LETTER\"}",
        "/api/v1/ai/refine                 | {\"currentContent\":\"a\",\"userMessage\":\"b\"}",
        "/api/v1/ai/skill-gaps             | {}",
        "/api/v1/ai/review                 | {\"currentContent\":\"a\"}",
        "/api/v1/ai/analyze                | {}",
    })
    void every_deferred_ai_endpoint_waits_as_long_as_configured(String path, String body)
            throws Exception {
        MvcResult started = mvc.perform(post(path)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(request().asyncStarted())
                .andReturn();

        assertThat(started.getRequest().getAsyncContext().getTimeout())
                .isEqualTo(CONFIGURED_TIMEOUT_MS);
    }
}
