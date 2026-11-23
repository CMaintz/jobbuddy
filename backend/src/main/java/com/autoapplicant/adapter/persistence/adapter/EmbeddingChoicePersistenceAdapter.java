package com.autoapplicant.adapter.persistence.adapter;

import com.autoapplicant.domain.ai.EmbeddingChoice;
import com.autoapplicant.port.out.ai.EmbeddingChoiceRepositoryPort;
import java.util.List;
import java.util.Optional;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

/** The single embedding_choice row (V047). Plain JDBC: one row, two columns, no entity needed. */
@Component
public class EmbeddingChoicePersistenceAdapter implements EmbeddingChoiceRepositoryPort {

    private final JdbcTemplate jdbc;

    public EmbeddingChoicePersistenceAdapter(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    @Override
    public Optional<EmbeddingChoice> find() {
        List<EmbeddingChoice> rows = jdbc.query("SELECT provider, model FROM embedding_choice WHERE id = 1",
                (rs, n) -> new EmbeddingChoice(rs.getString("provider"), rs.getString("model")));
        return rows.stream().findFirst();
    }

    @Override
    public void save(EmbeddingChoice choice) {
        jdbc.update("""
                INSERT INTO embedding_choice (id, provider, model, updated_at) VALUES (1, ?, ?, now())
                ON CONFLICT (id) DO UPDATE
                    SET provider = EXCLUDED.provider, model = EXCLUDED.model, updated_at = now()
                """, choice.provider(), choice.model());
    }
}
