package com.nexswitch.adapters.outbound.persistence;

import com.nexswitch.domain.model.vo.PanHash;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.jdbc.core.ResultSetExtractor;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@Tag("unit")
@ExtendWith(MockitoExtension.class)
class PostgresAtcWatermarkAdapterTest {

    private static final PanHash PAN = PanHash.of("a".repeat(64));

    @Mock private NamedParameterJdbcTemplate jdbc;

    private PostgresAtcWatermarkAdapter adapter;

    @BeforeEach
    void setUp() {
        adapter = new PostgresAtcWatermarkAdapter(jdbc);
    }

    @Test
    void isAtcFresh_returnsTrue_whenNoPriorWatermark() {
        // No row found in DB → null → first use → always fresh
        when(jdbc.query(anyString(), any(MapSqlParameterSource.class), any(ResultSetExtractor.class)))
                .thenReturn(null);

        boolean fresh = adapter.isAtcFresh(PAN, 100);

        assertThat(fresh).isTrue();
    }

    @Test
    void isAtcFresh_returnsTrue_whenAtcIsHigherThanStored() {
        when(jdbc.query(anyString(), any(MapSqlParameterSource.class), any(ResultSetExtractor.class)))
                .thenReturn(50);

        boolean fresh = adapter.isAtcFresh(PAN, 51);

        assertThat(fresh).isTrue();
    }

    @Test
    void isAtcFresh_returnsFalse_whenAtcEqualsStored() {
        // Same ATC = potential replay
        when(jdbc.query(anyString(), any(MapSqlParameterSource.class), any(ResultSetExtractor.class)))
                .thenReturn(100);

        boolean fresh = adapter.isAtcFresh(PAN, 100);

        assertThat(fresh).isFalse();
    }

    @Test
    void isAtcFresh_returnsFalse_whenAtcIsLowerThanStored() {
        when(jdbc.query(anyString(), any(MapSqlParameterSource.class), any(ResultSetExtractor.class)))
                .thenReturn(200);

        boolean fresh = adapter.isAtcFresh(PAN, 100);

        assertThat(fresh).isFalse();
    }

    @Test
    void updateWatermark_executesUpsert() {
        adapter.updateWatermark(PAN, 150);

        verify(jdbc).update(anyString(), any(MapSqlParameterSource.class));
    }
}
