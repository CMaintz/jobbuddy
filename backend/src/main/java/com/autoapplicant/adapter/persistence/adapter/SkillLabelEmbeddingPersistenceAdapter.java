package com.autoapplicant.adapter.persistence.adapter;

import com.autoapplicant.port.out.ai.SkillLabelEmbeddingRepositoryPort;
import java.sql.Array;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

/** The skill_label_embeddings cache (V048). Plain JDBC: rows are read by key, never searched. */
@Component
public class SkillLabelEmbeddingPersistenceAdapter implements SkillLabelEmbeddingRepositoryPort {

    private final JdbcTemplate jdbc;

    public SkillLabelEmbeddingPersistenceAdapter(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    @Override
    public Map<String, float[]> find(String model, Collection<String> labels) {
        Map<String, float[]> found = new HashMap<>();
        if (labels.isEmpty()) return found;
        jdbc.query("SELECT label, embedding FROM skill_label_embeddings"
                        + " WHERE model = ? AND label = ANY (?)",
                (ResultSet rs) -> {
                    found.put(rs.getString("label"), floats(rs.getArray("embedding")));
                }, model, labels.toArray(String[]::new));
        return found;
    }

    // Two feeds can miss the same label at once; the first write wins and both read the same.
    @Override
    public void saveAll(String model, Map<String, float[]> vectors) {
        List<Map.Entry<String, float[]>> rows = new ArrayList<>(vectors.entrySet());
        jdbc.batchUpdate("INSERT INTO skill_label_embeddings (model, label, embedding)"
                        + " VALUES (?, ?, ?) ON CONFLICT (model, label) DO NOTHING",
                rows, rows.size(), (statement, row) -> {
                    statement.setString(1, model);
                    statement.setString(2, row.getKey());
                    statement.setArray(3, statement.getConnection()
                            .createArrayOf("real", boxed(row.getValue())));
                });
    }

    private static float[] floats(Array array) throws SQLException {
        Object[] values = (Object[]) array.getArray();
        float[] out = new float[values.length];
        for (int i = 0; i < values.length; i++) out[i] = ((Number) values[i]).floatValue();
        return out;
    }

    private static Float[] boxed(float[] vector) {
        Float[] out = new Float[vector.length];
        for (int i = 0; i < vector.length; i++) out[i] = vector[i];
        return out;
    }
}
