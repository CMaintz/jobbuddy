package com.autoapplicant.adapter.web.controller;

import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.not;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.autoapplicant.adapter.security.SecurityContextHelper;
import com.autoapplicant.port.in.auth.ProvisionFirebaseUserUseCase;
import com.autoapplicant.port.in.auth.ResolveLinkedInUserUseCase;
import com.autoapplicant.port.out.ai.ChatProviderPort;
import com.autoapplicant.usecase.document.ParseCvService;
import com.autoapplicant.usecase.document.ParsedCvContactWriter;
import com.autoapplicant.usecase.skills.ImpliedSkillQueue;
import com.autoapplicant.usecase.skills.ProfileSkillIngestion;
import com.google.firebase.auth.FirebaseAuth;
import java.io.ByteArrayOutputStream;
import java.util.UUID;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.PDPage;
import org.apache.pdfbox.pdmodel.PDPageContentStream;
import org.apache.pdfbox.pdmodel.font.PDType1Font;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

/**
 * A CV that cannot be parsed is an actionable 4xx/503 with a reason code, never "Internal server
 * error" and never the provider's own output. Runs the real parse service behind the controller.
 */
@WebMvcTest(controllers = CvPdfImportController.class)
@Import(ParseCvService.class)
@AutoConfigureMockMvc(addFilters = false)
class CvPdfImportControllerTest {

    private static final String URL = "/api/v1/profile/import/cv-pdf";
    private static final String PROVIDER_SECRET = "secret stderr from the CLI agent";

    @Autowired MockMvc mvc;

    @MockitoBean(name = "generationAiProvider") ChatProviderPort aiProvider;
    @MockitoBean ParsedCvContactWriter        contactWriter;
    @MockitoBean ImpliedSkillQueue            impliedSkills;
    @MockitoBean ProfileSkillIngestion        skillIngestion;
    @MockitoBean SecurityContextHelper        secCtx;
    @MockitoBean ProvisionFirebaseUserUseCase provisionUser;
    @MockitoBean ResolveLinkedInUserUseCase   resolveLinkedInUser;
    @MockitoBean FirebaseAuth                 firebaseAuth;

    @BeforeEach
    void setUp() {
        when(secCtx.getCurrentUserId()).thenReturn(UUID.randomUUID());
    }

    @Test
    void a_pdf_with_no_text_is_422_no_text_and_never_reaches_the_ai() throws Exception {
        mvc.perform(multipart(URL).file(pdf(pdfWithText(null))))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.status").value(422))
                .andExpect(jsonPath("$.code").value("NO_TEXT"));

        verifyNoInteractions(aiProvider, contactWriter, skillIngestion);
    }

    @Test
    void a_provider_failure_is_503_and_does_not_leak_the_provider_output() throws Exception {
        when(aiProvider.generate(any(), any())).thenThrow(new IllegalStateException(PROVIDER_SECRET));

        mvc.perform(multipart(URL).file(pdf(pdfWithText("Jane Doe, senior engineer"))))
                .andExpect(status().isServiceUnavailable())
                .andExpect(jsonPath("$.status").value(503))
                .andExpect(jsonPath("$.code").value("AI_UNAVAILABLE"))
                .andExpect(content().string(not(containsString(PROVIDER_SECRET))));

        verifyNoInteractions(contactWriter, skillIngestion);
    }

    @Test
    void a_file_that_is_not_a_pdf_is_400_and_never_reaches_the_ai() throws Exception {
        mvc.perform(multipart(URL).file(pdf("not a pdf".getBytes())))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.status").value(400));

        verifyNoInteractions(aiProvider);
    }

    private static MockMultipartFile pdf(byte[] content) {
        return new MockMultipartFile("file", "cv.pdf", "application/pdf", content);
    }

    /** A one-page PDF carrying {@code text}, or a blank page when it is null. */
    private static byte[] pdfWithText(String text) throws Exception {
        try (PDDocument doc = new PDDocument(); ByteArrayOutputStream out = new ByteArrayOutputStream()) {
            PDPage page = new PDPage();
            doc.addPage(page);
            if (text != null) {
                try (PDPageContentStream stream = new PDPageContentStream(doc, page)) {
                    stream.beginText();
                    stream.setFont(PDType1Font.HELVETICA, 12);
                    stream.newLineAtOffset(72, 700);
                    stream.showText(text);
                    stream.endText();
                }
            }
            doc.save(out);
            return out.toByteArray();
        }
    }
}
