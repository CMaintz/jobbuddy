package com.autoapplicant.adapter.web.controller;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.autoapplicant.adapter.security.SecurityContextHelper;
import com.autoapplicant.domain.common.CvParseException;
import com.autoapplicant.port.in.auth.ProvisionFirebaseUserUseCase;
import com.autoapplicant.port.in.auth.ResolveLinkedInUserUseCase;
import com.autoapplicant.port.in.document.ParseCvUseCase;
import com.google.firebase.auth.FirebaseAuth;
import java.io.ByteArrayOutputStream;
import java.util.UUID;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.PDPage;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

/** A CV that cannot be parsed is an actionable 4xx, not "Internal server error". */
@WebMvcTest(controllers = CvPdfImportController.class)
@AutoConfigureMockMvc(addFilters = false)
class CvPdfImportControllerTest {

    private static final String URL = "/api/v1/profile/import/cv-pdf";
    private static final String PARSE_FAILURE = "The CV could not be parsed right now.";

    @Autowired MockMvc mvc;

    @MockitoBean ParseCvUseCase               parseCv;
    @MockitoBean SecurityContextHelper        secCtx;
    @MockitoBean ProvisionFirebaseUserUseCase provisionUser;
    @MockitoBean ResolveLinkedInUserUseCase   resolveLinkedInUser;
    @MockitoBean FirebaseAuth                 firebaseAuth;

    UUID userId = UUID.randomUUID();

    @BeforeEach
    void setUp() {
        when(secCtx.getCurrentUserId()).thenReturn(userId);
    }

    @Test
    void a_parse_failure_is_422_with_the_actionable_message() throws Exception {
        when(parseCv.parseCvText(eq(userId), any()))
                .thenThrow(new CvParseException(PARSE_FAILURE, new IllegalStateException("secret")));

        mvc.perform(multipart(URL).file(pdf(blankPdf())))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.status").value(422))
                .andExpect(jsonPath("$.message").value(PARSE_FAILURE));
    }

    @Test
    void a_file_that_is_not_a_pdf_is_400_and_never_reaches_the_parser() throws Exception {
        mvc.perform(multipart(URL).file(pdf("not a pdf".getBytes())))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.status").value(400));

        verifyNoInteractions(parseCv);
    }

    private static MockMultipartFile pdf(byte[] content) {
        return new MockMultipartFile("file", "cv.pdf", "application/pdf", content);
    }

    private static byte[] blankPdf() throws Exception {
        try (PDDocument doc = new PDDocument(); ByteArrayOutputStream out = new ByteArrayOutputStream()) {
            doc.addPage(new PDPage());
            doc.save(out);
            return out.toByteArray();
        }
    }
}
