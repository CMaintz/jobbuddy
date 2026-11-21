package com.autoapplicant.adapter.web.controller;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.autoapplicant.adapter.pdf.PdfRenderingService;
import com.autoapplicant.adapter.security.SecurityContextHelper;
import com.autoapplicant.config.AppDataDirectory;
import com.autoapplicant.domain.document.DocumentType;
import com.autoapplicant.domain.document.structured.DocumentIdentity;
import com.autoapplicant.domain.document.structured.DocumentRenderOptions;
import com.autoapplicant.domain.document.structured.StructuredDocument;
import com.autoapplicant.domain.document.structured.StructuredDocumentItem;
import com.autoapplicant.domain.document.structured.StructuredDocumentSection;
import com.autoapplicant.port.in.auth.ProvisionFirebaseUserUseCase;
import com.autoapplicant.port.in.auth.ResolveLinkedInUserUseCase;
import com.autoapplicant.port.in.document.BuildApplicationDocumentUseCase;
import com.autoapplicant.port.in.document.ManagePdfTemplatesUseCase;
import com.autoapplicant.port.in.user.GetUserProfileUseCase;
import com.autoapplicant.port.in.user.ManageProfilePrivateInfoUseCase;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.google.firebase.auth.FirebaseAuth;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.stream.IntStream;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;

/**
 * The export tells the caller how many pages the PDF has against its budget (a letter 1 page, a
 * CV 2), so the user learns of an overlong document instead of it only reaching a log. Renders
 * for real: each fixture's exact page count is asserted, so an off-by-one budget shows up.
 */
@WebMvcTest(controllers = PdfExportController.class)
@Import({PdfRenderingService.class, AppDataDirectory.class})
@AutoConfigureMockMvc(addFilters = false)
class PdfExportPageBudgetTest {

    private static final String URL = "/api/v1/documents/export-structured-pdf";
    private static final String PARAGRAPH = "Jeg leder kommunikation og compliance-arbejde, hvor "
            + "klare budskaber og ordentlig dokumentation betyder noget for kunder og kolleger. ";

    private static final int LETTER_PARAGRAPHS_FOR_ONE_PAGE = 3;
    private static final int LETTER_PARAGRAPHS_FOR_TWO_PAGES = 14;
    private static final int CV_ROLES_FOR_TWO_PAGES = 7;
    private static final int CV_ROLES_FOR_THREE_PAGES = 11;

    @Autowired MockMvc mvc;
    @Autowired ObjectMapper json;

    @MockitoBean ManagePdfTemplatesUseCase       pdfTemplates;
    @MockitoBean GetUserProfileUseCase           getUserProfile;
    @MockitoBean ManageProfilePrivateInfoUseCase privateInfo;
    @MockitoBean BuildApplicationDocumentUseCase buildAppDocument;
    @MockitoBean SecurityContextHelper           secCtx;
    @MockitoBean ProvisionFirebaseUserUseCase    provisionUser;
    @MockitoBean ResolveLinkedInUserUseCase      resolveLinkedInUser;
    @MockitoBean FirebaseAuth                    firebaseAuth;

    @Test
    void a_two_page_letter_reports_over_budget() throws Exception {
        export(letter(LETTER_PARAGRAPHS_FOR_TWO_PAGES))
                .andExpect(header().string("X-Page-Count", "2"))
                .andExpect(header().string("X-Page-Budget", "1"))
                .andExpect(header().string("X-Page-Over-Budget", "true"));
    }

    @Test
    void a_one_page_letter_is_within_budget() throws Exception {
        export(letter(LETTER_PARAGRAPHS_FOR_ONE_PAGE))
                .andExpect(header().string("X-Page-Count", "1"))
                .andExpect(header().string("X-Page-Budget", "1"))
                .andExpect(header().string("X-Page-Over-Budget", "false"));
    }

    @Test
    void a_two_page_cv_is_within_budget() throws Exception {
        export(cv(CV_ROLES_FOR_TWO_PAGES))
                .andExpect(header().string("X-Page-Count", "2"))
                .andExpect(header().string("X-Page-Budget", "2"))
                .andExpect(header().string("X-Page-Over-Budget", "false"));
    }

    @Test
    void a_three_page_cv_reports_over_budget() throws Exception {
        export(cv(CV_ROLES_FOR_THREE_PAGES))
                .andExpect(header().string("X-Page-Count", "3"))
                .andExpect(header().string("X-Page-Budget", "2"))
                .andExpect(header().string("X-Page-Over-Budget", "true"));
    }


    private ResultActions export(StructuredDocument document) throws Exception {
        String body = json.writeValueAsString(Map.of("document", document));
        return mvc.perform(post(URL).contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isOk());
    }

    private static StructuredDocument letter(int paragraphs) {
        String body = String.join("\n\n", Collections.nCopies(paragraphs, PARAGRAPH.repeat(3)));
        return document(DocumentType.COVER_LETTER, List.of(), body);
    }

    private static StructuredDocument cv(int roles) {
        List<StructuredDocumentItem> items = IntStream.range(0, roles).mapToObj(i -> role()).toList();
        StructuredDocumentSection experience =
                new StructuredDocumentSection("exp", "experience", "Erfaring", null, items);
        return document(DocumentType.CV, List.of(experience), null);
    }

    private static StructuredDocumentItem role() {
        List<String> bullets = Collections.nCopies(4, PARAGRAPH);
        return new StructuredDocumentItem(null, "Kommunikationskonsulent", "Firma A/S", "Aarhus",
                "2019 - 2024", PARAGRAPH, bullets, List.of(), List.of(), List.of(), null);
    }

    private static StructuredDocument document(
            DocumentType type, List<StructuredDocumentSection> sections, String body) {
        DocumentIdentity identity = new DocumentIdentity("Test Person", "Kommunikation",
                "test@example.com", null, "Aarhus", null, null, null, null);
        return new StructuredDocument(null, type, "ATS", null, identity,
                DocumentRenderOptions.defaults(), sections, body, null);
    }
}
