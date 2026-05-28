package com.nexswitch.settlement.rest;

import com.nexswitch.settlement.service.SettlementJobResult;
import com.nexswitch.settlement.service.SettlementJobService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDate;

// LEARN: Manual trigger endpoint — nightly @Scheduled cron handles production; this POST endpoint
//        lets ops re-run settlement for a specific date without restarting the service.
//        The scheduler calls the same SettlementJobService, so there is no logic duplication.
@RestController
@RequestMapping("/settlement")
public class SettlementController {

    private static final Logger log = LoggerFactory.getLogger(SettlementController.class);

    private final SettlementJobService settlementJobService;

    public SettlementController(SettlementJobService settlementJobService) {
        this.settlementJobService = settlementJobService;
    }

    @PostMapping("/run")
    public ResponseEntity<SettlementJobResult> trigger(
            @RequestParam(required = false)
            @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate date) {
        LocalDate targetDate = date != null ? date : LocalDate.now();
        log.info("settlement.manual_trigger date={}", targetDate);
        SettlementJobResult result = settlementJobService.runSettlementJob(targetDate);
        return ResponseEntity.ok(result);
    }
}
