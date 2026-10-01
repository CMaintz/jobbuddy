package com.autoapplicant.adapter.persistence.adapter;

import static org.assertj.core.api.Assertions.assertThat;

import com.autoapplicant.adapter.persistence.PostgresIntegrationTest;
import com.autoapplicant.domain.ai.AiOperationUsage;
import com.autoapplicant.domain.ai.AiUsageRecord;
import com.autoapplicant.domain.ai.AiUsageTotals;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;

@Import(AiUsagePersistenceAdapter.class)
class AiUsagePersistenceAdapterTest extends PostgresIntegrationTest {

    private static final String MODEL = "gpt-4o";

    @Autowired AiUsagePersistenceAdapter usage;
    @Autowired JdbcTemplate jdbc;

    UUID userId;

    @BeforeEach
    void setUp() {
        userId = UUID.randomUUID();
        jdbc.update("INSERT INTO users (id, email) VALUES (?, ?)", userId, userId + "@example.com");
    }

    @Test
    void totals_sum_tokens_and_count_requests() {
        record("COVER_LETTER", 100, 40);
        record("TAILORED_CV", 200, 60);

        AiUsageTotals totals = usage.totalsSince(userId, Instant.now().minus(1, ChronoUnit.DAYS));

        assertThat(totals).isEqualTo(new AiUsageTotals(300, 100, 2));
    }

    @Test
    void totals_without_a_window_cover_all_time_and_are_zero_for_a_new_user() {
        assertThat(usage.totalsSince(userId, null)).isEqualTo(AiUsageTotals.NONE);

        record("COVER_LETTER", 10, 5);

        assertThat(usage.totalsSince(userId, null)).isEqualTo(new AiUsageTotals(10, 5, 1));
    }

    @Test
    void by_operation_groups_and_orders_by_token_spend() {
        record("COVER_LETTER", 10, 5);
        record("TAILORED_CV", 500, 100);
        record("COVER_LETTER", 20, 5);

        List<AiOperationUsage> rows = usage.byOperation(userId);

        assertThat(rows).containsExactly(
                new AiOperationUsage("TAILORED_CV", 500, 100, 1),
                new AiOperationUsage("COVER_LETTER", 30, 10, 2));
    }

    private void record(String operation, int tokensIn, int tokensOut) {
        usage.save(new AiUsageRecord(null, userId, MODEL, tokensIn, tokensOut, operation, null));
    }
}
