package com.autoapplicant.adapter.persistence.adapter;

import com.autoapplicant.port.out.skills.AdjacentSkillRunRepositoryPort;
import java.util.Optional;
import java.util.UUID;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

/** The adjacent_skill_runs rows (V048). Plain JDBC: one row per user, one value. */
@Component
public class AdjacentSkillRunPersistenceAdapter implements AdjacentSkillRunRepositoryPort {

    private final JdbcTemplate jdbc;

    public AdjacentSkillRunPersistenceAdapter(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    @Override
    public Optional<String> lastFingerprint(UUID userId) {
        return jdbc.queryForList("SELECT skills_fingerprint FROM adjacent_skill_runs"
                + " WHERE user_id = ?", String.class, userId).stream().findFirst();
    }

    @Override
    public void record(UUID userId, String fingerprint) {
        jdbc.update("""
                INSERT INTO adjacent_skill_runs (user_id, skills_fingerprint, updated_at)
                VALUES (?, ?, now())
                ON CONFLICT (user_id) DO UPDATE
                    SET skills_fingerprint = EXCLUDED.skills_fingerprint, updated_at = now()
                """, userId, fingerprint);
    }
}
