package com.autoapplicant.usecase.document;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * The one code-fence stripper.
 *
 * <p>Five callers each carried their own copy of this, and they were not equivalent — some used
 * replace, others replaceAll, and they disagreed about unterminated fences. The same malformed
 * model response was therefore cleaned differently depending on which feature received it. These
 * tests give the surviving implementation the contract the copies never had.
 */
class AiResponseParserTest {

    @Test
    void a_fenced_response_loses_its_fence() {
        assertThat(AiResponseParser.stripCodeFence("```\n{\"a\":1}\n```")).isEqualTo("{\"a\":1}");
    }

    @Test
    void a_language_tagged_fence_loses_the_tag_too() {
        assertThat(AiResponseParser.stripCodeFence("```json\n{\"a\":1}\n```")).isEqualTo("{\"a\":1}");
    }

    @Test
    void an_unfenced_response_is_returned_untouched() {
        assertThat(AiResponseParser.stripCodeFence("{\"a\":1}")).isEqualTo("{\"a\":1}");
    }

    @Test
    void an_unterminated_fence_is_left_alone_rather_than_half_stripped() {
        // Better to hand the parser something recognisably wrong than something silently truncated.
        String truncated = "```json\n{\"a\":1}";
        assertThat(AiResponseParser.stripCodeFence(truncated)).isEqualTo(truncated);
    }

    @Test
    void a_fence_inside_the_body_does_not_confuse_the_outer_one() {
        String response = "```\n{\"note\":\"use ``` for code\"}\n```";
        assertThat(AiResponseParser.stripCodeFence(response)).isEqualTo("{\"note\":\"use ``` for code\"}");
    }

    @Test
    void extracting_an_object_tolerates_prose_around_it() {
        assertThat(AiResponseParser.extractJsonObject("Sure! ```json\n{\"a\":1}\n``` hope that helps"))
                .isEqualTo("{\"a\":1}");
    }
}
