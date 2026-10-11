package com.autoapplicant.usecase.document;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import com.autoapplicant.domain.common.CvParseException;
import com.autoapplicant.domain.common.CvParseException.Reason;
import com.autoapplicant.domain.skill.ParsedSkillSuggestion;
import com.autoapplicant.domain.user.Profile;
import com.autoapplicant.port.out.ai.ChatProviderPort;
import com.autoapplicant.usecase.ai.AiOperations;
import com.autoapplicant.usecase.skills.ImpliedSkillQueue;
import com.autoapplicant.usecase.skills.ProfileSkillIngestion;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class ParseCvServiceTest {

    @Mock ChatProviderPort      aiProvider;
    @Mock ParsedCvContactWriter writer;
    @Mock ImpliedSkillQueue     impliedSkills;
    @Mock ProfileSkillIngestion skillIngestion;

    ParseCvService service;
    UUID userId = UUID.randomUUID();

    @BeforeEach
    void setUp() {
        service = new ParseCvService(aiProvider, new ObjectMapper(), writer,
                impliedSkills, skillIngestion);
    }

    @Test
    void hands_the_parsed_json_to_the_writer_and_returns_the_profile() {
        String json = """
                {"fullName":"Jane Doe","headline":"Senior Engineer","summary":"Ten years.",
                 "skills":["Java"],"languages":["Danish","English"],"interests":["Chess"]}""";
        when(aiProvider.generate(any(), eq(AiOperations.CV_PARSE))).thenReturn(json);

        Profile result = service.parseCvText(userId, "raw cv text");

        ArgumentCaptor<JsonNode> node = ArgumentCaptor.forClass(JsonNode.class);
        verify(writer).write(eq(userId), node.capture());
        assertThat(node.getValue().get("fullName").asText()).isEqualTo("Jane Doe");
        assertThat(result.userId()).isEqualTo(userId);
        assertThat(result.headline()).isEqualTo("Senior Engineer");
        assertThat(result.summary()).isEqualTo("Ten years.");
        assertThat(result.languages()).containsExactly("Danish", "English");
        assertThat(result.interests()).containsExactly("Chess");
    }

    @Test
    void ingests_stated_skills_and_queues_implied_ones() {
        when(aiProvider.generate(any(), any()))
                .thenReturn("{\"skills\":[\"Java\",\"Spring\"],\"technologies\":[\"Docker\"]}");

        service.parseCvText(userId, "raw");

        verify(skillIngestion).ingest(userId, List.of("Java", "Spring", "Docker"));
        verify(impliedSkills).queue(eq(userId), any(), eq(List.of("Java", "Spring", "Docker")),
                eq(ParsedSkillSuggestion.Source.CV_PARSE));
    }

    @Test
    void strips_a_code_fence_around_the_json() {
        when(aiProvider.generate(any(), any())).thenReturn("```json\n{\"headline\":\"Dev\"}\n```");

        Profile result = service.parseCvText(userId, "raw");

        assertThat(result.headline()).isEqualTo("Dev");
    }

    @Test
    void blank_cv_text_is_rejected_without_calling_the_ai() {
        assertThatThrownBy(() -> service.parseCvText(userId, "  \n "))
                .isInstanceOf(CvParseException.class)
                .hasFieldOrPropertyWithValue("reason", Reason.NO_TEXT)
                .hasMessage(ParseCvService.NO_TEXT_MESSAGE);

        verifyNoInteractions(aiProvider, writer);
    }

    @Test
    void a_provider_failure_surfaces_as_a_parse_error_and_writes_nothing() {
        IllegalStateException cause = new IllegalStateException("CLI agent exited 1");
        when(aiProvider.generate(any(), any())).thenThrow(cause);

        assertThatThrownBy(() -> service.parseCvText(userId, "raw"))
                .isInstanceOf(CvParseException.class)
                .hasFieldOrPropertyWithValue("reason", Reason.AI_UNAVAILABLE)
                .hasMessage(ParseCvService.AI_UNAVAILABLE_MESSAGE)
                .hasCause(cause);

        verify(writer, never()).write(any(), any());
    }

    @Test
    void a_response_that_is_not_json_surfaces_as_a_parse_error_and_writes_nothing() {
        when(aiProvider.generate(any(), any())).thenReturn("sorry, I cannot help with that");

        assertThatThrownBy(() -> service.parseCvText(userId, "raw"))
                .isInstanceOf(CvParseException.class)
                .hasFieldOrPropertyWithValue("reason", Reason.UNREADABLE)
                .hasMessage(ParseCvService.UNREADABLE_RESPONSE_MESSAGE);

        verify(writer, never()).write(any(), any());
    }

    @Test
    void a_json_value_that_is_not_an_object_surfaces_as_a_parse_error() {
        when(aiProvider.generate(any(), any())).thenReturn("[\"Java\"]");

        assertThatThrownBy(() -> service.parseCvText(userId, "raw"))
                .isInstanceOf(CvParseException.class)
                .hasFieldOrPropertyWithValue("reason", Reason.UNREADABLE)
                .hasMessage(ParseCvService.UNREADABLE_RESPONSE_MESSAGE);

        verify(writer, never()).write(any(), any());
    }

    @Test
    void a_null_response_surfaces_as_a_parse_error() {
        when(aiProvider.generate(any(), any())).thenReturn(null);

        assertThatThrownBy(() -> service.parseCvText(userId, "raw"))
                .isInstanceOf(CvParseException.class)
                .hasFieldOrPropertyWithValue("reason", Reason.UNREADABLE)
                .hasMessage(ParseCvService.UNREADABLE_RESPONSE_MESSAGE);
    }
}
