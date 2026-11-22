package com.autoapplicant.adapter.web.controller;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.asyncDispatch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.request;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.autoapplicant.adapter.security.SecurityContextHelper;
import com.autoapplicant.domain.document.structured.LetterGateNoVerdictException;
import com.autoapplicant.port.in.auth.ProvisionFirebaseUserUseCase;
import com.autoapplicant.port.in.auth.ResolveLinkedInUserUseCase;
import com.autoapplicant.port.in.document.RecheckLetterUseCase;
import com.google.firebase.auth.FirebaseAuth;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.ResultActions;

/** The export warning tells "nothing to check against" apart from a failure, by status code. */
@WebMvcTest(controllers = LetterGateController.class)
@AutoConfigureMockMvc(addFilters = false)
class LetterGateControllerTest {

    @Autowired MockMvc mvc;

    @MockitoBean RecheckLetterUseCase recheck;
    @MockitoBean SecurityContextHelper secCtx;
    @MockitoBean ProvisionFirebaseUserUseCase provisionUser;
    @MockitoBean ResolveLinkedInUserUseCase resolveLinkedInUser;
    @MockitoBean FirebaseAuth firebaseAuth;

    private ResultActions recheckFailingWith(RuntimeException cause) throws Exception {
        when(secCtx.getCurrentUserId()).thenReturn(UUID.randomUUID());
        when(recheck.recheck(any(), any())).thenReturn(CompletableFuture.failedFuture(cause));
        MvcResult started = mvc.perform(post("/api/v1/ai/documents/{id}/letter-gate", UUID.randomUUID()))
                .andExpect(request().asyncStarted())
                .andReturn();
        return mvc.perform(asyncDispatch(started));
    }

    @Test
    void aLetterWithNoPostingAnswersConflict() throws Exception {
        // Bug: the future's CompletionException reaches the handler still wrapped, so a letter
        // that simply cannot be checked comes back as a server error.
        recheckFailingWith(new IllegalStateException("no posting")).andExpect(status().isConflict());
    }

    @Test
    void someoneElsesLetterAnswersBadRequest() throws Exception {
        // Bug: as above, an unknown or foreign document id turns into a 500 instead of a 400.
        recheckFailingWith(new IllegalArgumentException("not found")).andExpect(status().isBadRequest());
    }

    @Test
    void aRecheckWithNoVerdictAnswersServiceUnavailable() throws Exception {
        // Bug: the outage reaches the client as a 500 with no way to tell it from a broken
        // request, or as a 200 carrying "no verdict" that the page stores over the failed points.
        recheckFailingWith(new LetterGateNoVerdictException()).andExpect(status().isServiceUnavailable());
    }
}
