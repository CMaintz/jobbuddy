package com.autoapplicant.adapter.persistence.type;

import com.pgvector.PGvector;
import org.hibernate.engine.spi.SharedSessionContractImplementor;
import org.hibernate.usertype.UserType;

import java.io.Serializable;
import java.sql.*;
import java.util.Arrays;

/**
 * Hibernate UserType that maps float[] to PostgreSQL's pgvector column type.
 *
 * @JdbcTypeCode(SqlTypes.VECTOR) on float[] doesn't properly wire up pgvector
 * serialization on Spring Boot 3.2 / Hibernate 6.4 without additional setup.
 * This type explicitly uses PGvector (a PGobject subclass) to set the value,
 * which the PostgreSQL JDBC driver knows how to bind to a vector column.
 */
public class VectorUserType implements UserType<float[]> {

    @Override
    public int getSqlType() {
        return Types.OTHER;
    }

    @Override
    public Class<float[]> returnedClass() {
        return float[].class;
    }

    @Override
    public boolean equals(float[] x, float[] y) {
        return Arrays.equals(x, y);
    }

    @Override
    public int hashCode(float[] x) {
        return Arrays.hashCode(x);
    }

    @Override
    public float[] nullSafeGet(ResultSet rs, int position,
                               SharedSessionContractImplementor session,
                               Object owner) throws SQLException {
        Object obj = rs.getObject(position);
        if (obj == null) return null;
        if (obj instanceof PGvector pv) return pv.toArray();
        // Fallback: parse "[v1,v2,...]" string representation
        String s = obj.toString();
        String[] parts = s.substring(1, s.length() - 1).split(",");
        float[] result = new float[parts.length];
        for (int i = 0; i < parts.length; i++) result[i] = Float.parseFloat(parts[i].trim());
        return result;
    }

    @Override
    public void nullSafeSet(PreparedStatement st, float[] value, int index,
                            SharedSessionContractImplementor session) throws SQLException {
        if (value == null) {
            st.setNull(index, Types.OTHER);
        } else {
            st.setObject(index, new PGvector(value));
        }
    }

    @Override
    public float[] deepCopy(float[] value) {
        return value != null ? Arrays.copyOf(value, value.length) : null;
    }

    @Override
    public boolean isMutable() {
        return true;
    }

    @Override
    public Serializable disassemble(float[] value) {
        return deepCopy(value);
    }

    @Override
    public float[] assemble(Serializable cached, Object owner) {
        return deepCopy((float[]) cached);
    }
}
