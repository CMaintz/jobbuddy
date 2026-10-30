package com.autoapplicant.usecase.application;

import static com.autoapplicant.domain.application.ApplicationStatus.*;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

import com.autoapplicant.domain.application.Application;
import com.autoapplicant.domain.application.ApplicationStatus;
import com.autoapplicant.domain.application.ApplicationStatusEvent;
import com.autoapplicant.domain.application.CreateApplicationCommand;
import com.autoapplicant.domain.document.DocumentType;
import com.autoapplicant.domain.document.GeneratedDocument;
import com.autoapplicant.port.out.application.ApplicationRepositoryPort;
import com.autoapplicant.port.out.application.ApplicationStatusEventRepositoryPort;
import com.autoapplicant.port.out.document.GeneratedDocumentRepositoryPort;
import java.time.Instant;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

/** Linking a generated document to an application, and what attaching one does to it. */
@ExtendWith(MockitoExtension.class)
class ApplicationServiceDocumentTest {

    private static final Instant FIRST_APPLIED = Instant.parse("2026-02-04T08:30:00Z");

    @Mock ApplicationRepositoryPort repo;
    @Mock ApplicationStatusEventRepositoryPort statusEventRepo;
    @Mock GeneratedDocumentRepositoryPort generatedDocuments;

    ApplicationService service;

    UUID userId = UUID.randomUUID();
    UUID jobId  = UUID.randomUUID();
    UUID appId  = UUID.randomUUID();

    @BeforeEach
    void setUp() {
        service = new ApplicationService(repo, statusEventRepo, generatedDocuments);
    }

    @Test
    void attach_links_the_document_to_the_application_and_keeps_its_other_fields() {
        GeneratedDocument document = generatedDocument(DocumentType.COVER_LETTER);
        givenApplication(application(SAVED, null));
        givenDocument(document);

        service.attachGeneratedDocument(appId, userId, document.id(), null, null, null);

        assertLinkedToApplication(document);
    }

    @Test
    void create_links_the_generated_document_to_the_new_application() {
        GeneratedDocument document = generatedDocument(DocumentType.COVER_LETTER);
        when(repo.save(any())).thenReturn(application(SAVED, null));
        givenDocument(document);

        service.createApplication(new CreateApplicationCommand(
                userId, jobId, null, null, document.id(), SAVED, null, null, null, null, null));

        assertLinkedToApplication(document);
    }

    @Test
    void attaching_someone_elses_document_fails() {
        UUID documentId = UUID.randomUUID();
        when(repo.findByIdAndUserId(appId, userId)).thenReturn(Optional.of(application(SAVED, null)));
        when(generatedDocuments.findByIdAndUserId(documentId, userId)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.attachGeneratedDocument(appId, userId, documentId, null, null, null))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("Generated document not found");
        verify(generatedDocuments, never()).save(any());
    }

    @ParameterizedTest
    @CsvSource({
            "COVER_LETTER,            coverLetter",
            "APPLICATION_TEXT,        applicationText",
            "UNSOLICITED_APPLICATION, applicationText",
            "RECRUITER_MESSAGE,       recruiterMessage",
            "FOLLOW_UP_MESSAGE,       recruiterMessage",
            "CV,                      none"
    })
    void the_document_lands_in_the_field_for_its_type_and_nowhere_else(DocumentType type, String field) {
        Application existing = application(SAVED, null).toBuilder()
                .coverLetterText("old letter").applicationText("old text").recruiterMessage("old message")
                .build();
        GeneratedDocument document = generatedDocument(type);
        givenApplication(existing);
        givenDocument(document);

        Application result = service.attachGeneratedDocument(appId, userId, document.id(), null, null, null);

        Map<String, String> fields = Map.of(
                "coverLetter", result.coverLetterText(),
                "applicationText", result.applicationText(),
                "recruiterMessage", result.recruiterMessage());
        Map<String, String> untouched = Map.of(
                "coverLetter", "old letter",
                "applicationText", "old text",
                "recruiterMessage", "old message");
        fields.forEach((name, value) -> assertThat(value).as(name)
                .isEqualTo(name.equals(field) ? "Dear Acme" : untouched.get(name)));
    }

    @Test
    void edited_content_from_the_caller_wins_over_the_stored_draft_unless_blank() {
        GeneratedDocument document = generatedDocument(DocumentType.COVER_LETTER);
        givenApplication(application(SAVED, null));
        givenDocument(document);

        Application edited = service.attachGeneratedDocument(appId, userId, document.id(),
                "Dear Acme, edited", null, null);
        Application blank = service.attachGeneratedDocument(appId, userId, document.id(), "  ", null, null);

        assertThat(edited.coverLetterText()).isEqualTo("Dear Acme, edited");
        assertThat(blank.coverLetterText()).isEqualTo("Dear Acme");
    }

    @Test
    void attaching_with_a_new_status_moves_the_application_and_records_the_move() {
        GeneratedDocument document = generatedDocument(DocumentType.COVER_LETTER);
        givenApplication(application(PREPARING, null));
        givenDocument(document);

        Application result = service.attachGeneratedDocument(appId, userId, document.id(), null,
                APPLIED, "Sent with the tailored letter");

        assertThat(result.status()).isEqualTo(APPLIED);
        assertThat(result.appliedAt()).isNotNull();
        ArgumentCaptor<ApplicationStatusEvent> cap = ArgumentCaptor.forClass(ApplicationStatusEvent.class);
        verify(statusEventRepo).save(cap.capture());
        assertThat(cap.getValue().fromStatus()).isEqualTo(PREPARING);
        assertThat(cap.getValue().toStatus()).isEqualTo(APPLIED);
        assertThat(cap.getValue().notes()).isEqualTo("Sent with the tailored letter");
        assertThat(cap.getValue().occurredAt()).isEqualTo(result.appliedAt());
    }

    @Test
    void attaching_without_a_status_change_writes_nothing_to_the_ledger() {
        GeneratedDocument document = generatedDocument(DocumentType.COVER_LETTER);
        givenApplication(application(PREPARING, null));
        givenDocument(document);

        service.attachGeneratedDocument(appId, userId, document.id(), null, null, null);

        verifyNoInteractions(statusEventRepo);
    }

    @Test
    void re_attaching_to_an_already_applied_application_keeps_the_original_applied_date() {
        GeneratedDocument document = generatedDocument(DocumentType.FOLLOW_UP_MESSAGE);
        givenApplication(application(APPLIED, FIRST_APPLIED));
        givenDocument(document);

        Application result = service.attachGeneratedDocument(appId, userId, document.id(), null, APPLIED, null);

        assertThat(result.appliedAt()).isEqualTo(FIRST_APPLIED);
        verifyNoInteractions(statusEventRepo);
    }

    private void givenApplication(Application application) {
        when(repo.findByIdAndUserId(appId, userId)).thenReturn(Optional.of(application));
        when(repo.save(any())).thenAnswer(inv -> inv.getArgument(0));
    }

    private void givenDocument(GeneratedDocument document) {
        when(generatedDocuments.findByIdAndUserId(document.id(), userId)).thenReturn(Optional.of(document));
        when(generatedDocuments.save(any())).thenAnswer(inv -> inv.getArgument(0));
    }

    private void assertLinkedToApplication(GeneratedDocument original) {
        ArgumentCaptor<GeneratedDocument> cap = ArgumentCaptor.forClass(GeneratedDocument.class);
        verify(generatedDocuments).save(cap.capture());
        assertThat(cap.getValue().applicationId()).isEqualTo(appId);
        assertThat(cap.getValue()).usingRecursiveComparison().ignoringFields("applicationId").isEqualTo(original);
    }

    /** Every field set, so a link that drops one shows up in the comparison. */
    private GeneratedDocument generatedDocument(DocumentType type) {
        return new GeneratedDocument(UUID.randomUUID(), userId, null, jobId, type,
                "Dear Acme", "{\"bodyContent\":\"Dear Acme\"}", "application-modern", "DESIGNED",
                UUID.randomUUID(), UUID.randomUUID(), "gpt-4o", 1234, Instant.parse("2026-01-02T03:04:05Z"));
    }

    private Application application(ApplicationStatus status, Instant appliedAt) {
        return new Application(appId, userId, jobId, status, appliedAt,
                null, null, null, null, null,
                null, null, null, null, null,
                Instant.now(), Instant.now(), null, null);
    }
}
