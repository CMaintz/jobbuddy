package com.autoapplicant.adapter.web;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

import com.autoapplicant.adapter.security.SecurityContextHelper;
import com.autoapplicant.adapter.web.controller.PdfTemplateController;
import com.autoapplicant.domain.common.NotFoundException;
import com.autoapplicant.port.in.auth.ProvisionFirebaseUserUseCase;
import com.autoapplicant.port.in.auth.ResolveLinkedInUserUseCase;
import com.autoapplicant.port.in.document.ManagePdfTemplatesUseCase;
import com.google.firebase.auth.FirebaseAuth;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.http.MediaType;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

/** Client mistakes are 4xx, not "Internal server error". */
@WebMvcTest(controllers = PdfTemplateController.class)
@AutoConfigureMockMvc(addFilters = false)
class GlobalExceptionHandlerTest {

    private static final String BASE = "/api/v1/pdf-templates";

    @Autowired MockMvc mvc;

    @MockitoBean ManagePdfTemplatesUseCase    service;
    @MockitoBean SecurityContextHelper        secCtx;
    @MockitoBean ProvisionFirebaseUserUseCase provisionUser;
    @MockitoBean ResolveLinkedInUserUseCase   resolveLinkedInUser;
    @MockitoBean FirebaseAuth                 firebaseAuth;

    UUID userId = UUID.randomUUID();
    UUID id = UUID.randomUUID();

    @BeforeEach
    void setUp() {
        when(secCtx.getCurrentUserId()).thenReturn(userId);
    }

    @Test
    void malformed_json_is_400() throws Exception {
        mvc.perform(put(BASE + "/" + id).contentType(MediaType.APPLICATION_JSON).content("{not json"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("Malformed request body"));
    }

    @Test
    void a_path_id_that_is_not_a_uuid_is_400() throws Exception {
        mvc.perform(get(BASE + "/not-a-uuid"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.status").value(400));
    }

    @Test
    void an_unsupported_method_is_405() throws Exception {
        mvc.perform(patch(BASE + "/" + id))
                .andExpect(status().isMethodNotAllowed())
                .andExpect(jsonPath("$.status").value(405));
    }

    @Test
    void an_unknown_route_is_404() throws Exception {
        mvc.perform(get("/api/v1/no-such-thing"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.status").value(404));
    }

    @Test
    void an_unsupported_content_type_is_415() throws Exception {
        mvc.perform(put(BASE + "/" + id).contentType(MediaType.TEXT_PLAIN).content("x"))
                .andExpect(status().isUnsupportedMediaType());
    }

    @Test
    void a_record_the_caller_does_not_own_is_404() throws Exception {
        when(service.update(any())).thenThrow(new NotFoundException("Template not found"));

        mvc.perform(put(BASE + "/" + id).contentType(MediaType.APPLICATION_JSON).content("{\"name\":\"x\"}"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.message").value("Template not found"));
    }

    @Test
    void access_denied_is_403() throws Exception {
        when(service.getTemplates(eq(userId))).thenThrow(new AccessDeniedException("Access Denied"));

        mvc.perform(get(BASE)).andExpect(status().isForbidden());
    }

    @Test
    void a_genuine_failure_is_still_a_500_without_details() throws Exception {
        when(service.getTemplates(eq(userId))).thenThrow(new RuntimeException("db password is hunter2"));

        mvc.perform(get(BASE))
                .andExpect(status().isInternalServerError())
                .andExpect(jsonPath("$.message").value("Internal server error"));
    }
}
