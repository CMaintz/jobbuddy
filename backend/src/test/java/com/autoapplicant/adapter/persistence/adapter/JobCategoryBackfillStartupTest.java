package com.autoapplicant.adapter.persistence.adapter;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.sql.SQLException;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.BadSqlGrammarException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionStatus;

/**
 * The crawler CLI runs with Flyway off, so it can start against a database that has not reached
 * V042. A startup listener that throws turns an already successful crawl into a failed run.
 */
class JobCategoryBackfillStartupTest {

    @Test
    void a_database_without_the_marker_table_is_skipped_and_the_version_left_unclaimed() {
        JdbcTemplate jdbc = mock(JdbcTemplate.class);
        PlatformTransactionManager transactions = mock(PlatformTransactionManager.class);
        TransactionStatus status = mock(TransactionStatus.class);
        when(transactions.getTransaction(any())).thenReturn(status);
        when(jdbc.update(anyString(), eq(JobCategoryBackfill.RULES_VERSION))).thenThrow(
                new BadSqlGrammarException("claim", "INSERT INTO job_category_backfill",
                        new SQLException("relation \"job_category_backfill\" does not exist")));

        JobCategoryBackfill backfill = new JobCategoryBackfill(jdbc, transactions);

        assertThatCode(backfill::onStartup).doesNotThrowAnyException();
        verify(transactions).rollback(status);
        verify(transactions, never()).commit(any());
    }
}
