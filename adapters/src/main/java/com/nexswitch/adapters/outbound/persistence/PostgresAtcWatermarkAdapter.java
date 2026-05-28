package com.nexswitch.adapters.outbound.persistence;

import com.nexswitch.domain.model.vo.PanHash;
import com.nexswitch.domain.port.outbound.AtcWatermarkPort;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Repository;

// LEARN: UPSERT — "INSERT ... ON CONFLICT DO UPDATE" is atomic in PostgreSQL; avoids a
//        SELECT + conditional INSERT race condition that could let a replayed ATC slip through.
//        GREATEST(max_atc, :atc) ensures concurrent updates always advance the watermark forward.
@Repository
public class PostgresAtcWatermarkAdapter implements AtcWatermarkPort {

    private static final String SELECT_SQL =
            "SELECT last_seen_atc FROM pan_atc_watermarks WHERE pan_hash = :panHash";

    private static final String UPSERT_SQL =
            """
            INSERT INTO pan_atc_watermarks (pan_hash, last_seen_atc, updated_at)
            VALUES (:panHash, :atc, NOW())
            ON CONFLICT (pan_hash) DO UPDATE
                SET last_seen_atc = GREATEST(pan_atc_watermarks.last_seen_atc, :atc),
                    updated_at    = NOW()
            """;

    private final NamedParameterJdbcTemplate jdbc;

    public PostgresAtcWatermarkAdapter(NamedParameterJdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    @Override
    public boolean isAtcFresh(PanHash panHash, int atc) {
        MapSqlParameterSource params = new MapSqlParameterSource("panHash", panHash.value());
        Integer storedMax = jdbc.query(SELECT_SQL, params, rs -> {
            if (rs.next()) return rs.getInt("last_seen_atc");
            return null;
        });
        // No watermark yet → first use → always fresh
        if (storedMax == null) return true;
        // LEARN: strict greater-than — ATC == storedMax could be a replay of the exact same transaction
        return atc > storedMax;
    }

    @Override
    public void updateWatermark(PanHash panHash, int atc) {
        MapSqlParameterSource params = new MapSqlParameterSource()
                .addValue("panHash", panHash.value())
                .addValue("atc", atc);
        jdbc.update(UPSERT_SQL, params);
    }
}
