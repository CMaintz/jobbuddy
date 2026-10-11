package com.autoapplicant.usecase.document;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.autoapplicant.domain.user.Profile;
import com.autoapplicant.port.out.ai.ChatProviderPort;
import com.autoapplicant.usecase.ai.AiOperations;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class ParseCvServiceTest {

    @Mock ChatProviderPort aiProvider;
    @Mock ParsedCvWriter   writer;

    ParseCvService service;
    UUID userId = UUID.randomUUID();

    @BeforeEach
    void setUp() {
        service = new ParseCvService(aiProvider, new ObjectMapper(), writer);
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
    void returns_empty_profile_when_ai_response_is_not_valid_json() {
        when(aiProvider.generate(any(), any())).thenReturn("sorry, I cannot help with that");

        Profile result = service.parseCvText(userId, "raw");

        assertThat(result.userId()).isEqualTo(userId);
        assertThat(result.headline()).isNull();
        assertThat(result.languages()).isEmpty();
        verify(writer, never()).write(any(), any());
    }
}
