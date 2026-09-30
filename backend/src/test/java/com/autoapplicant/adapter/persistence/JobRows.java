package com.autoapplicant.adapter.persistence;

import java.sql.Timestamp;
import java.time.Instant;
import java.util.UUID;
import org.springframework.jdbc.core.JdbcTemplate;

/** Inserts minimal job rows straight into the table, leaving every defaulted column alone. */
public final class JobRows {

    private final JdbcTemplate jdbc;

    public JobRows(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    public UUID insert(String title, String description, Instant postedAt) {
        return insert(title, description, postedAt, true, null);
    }

    public UUID insert(String title, String description, Instant postedAt, boolean active, String category) {
        UUID id = UUID.randomUUID();
        jdbc.update("""
                INSERT INTO jobs (id, source, url, title, company_name, description_clean,
                                  posted_at, is_active, job_category)
                VALUES (?, 'JOBINDEX', ?, ?, 'Acme ApS', ?, ?, ?, ?)
                """,
                id, "https://example.com/jobs/" + id, title, description,
                Timestamp.from(postedAt), active, category);
        return id;
    }
}
