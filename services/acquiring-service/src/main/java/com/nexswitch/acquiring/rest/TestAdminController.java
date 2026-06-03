package com.nexswitch.acquiring.rest;

import org.springframework.context.annotation.Profile;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

// LEARN: Test-only admin controller — guarded by @Profile("local") so it is never compiled
//        into production deployments. Exposes TRUNCATE endpoints so QA scenarios that reuse
//        the same hardcoded PANs/ATCs can be re-run without accumulating stale state.
@Profile("local")
@RestController
@RequestMapping("/api/internal/test-admin")
public class TestAdminController {

    private final JdbcTemplate jdbc;

    public TestAdminController(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    @DeleteMapping("/atc-watermarks")
    public Map<String, Object> resetAtcWatermarks() {
        int rows = jdbc.update("TRUNCATE TABLE pan_atc_watermarks");
        return Map.of("deleted", rows, "table", "pan_atc_watermarks");
    }
}
