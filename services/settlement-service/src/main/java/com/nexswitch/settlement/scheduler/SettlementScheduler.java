package com.nexswitch.settlement.scheduler;

import com.nexswitch.settlement.service.SettlementJobService;
import com.nexswitch.settlement.service.SettlementJobResult;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.Clock;
import java.time.LocalDate;

// LEARN: Clock injection — injecting Clock instead of calling LocalDate.now() directly makes the
//        scheduler testable; tests pass a fixed Clock to control the "current date" without mocking statics.
@Component
public class SettlementScheduler {

    private static final Logger log = LoggerFactory.getLogger(SettlementScheduler.class);

    private final SettlementJobService settlementJobService;
    private final Clock clock;

    public SettlementScheduler(SettlementJobService settlementJobService, Clock clock) {
        this.settlementJobService = settlementJobService;
        this.clock                = clock;
    }

    @Scheduled(cron = "0 30 23 * * *", zone = "Asia/Kolkata")
    public void runNightlySettlement() {
        LocalDate today = LocalDate.now(clock);
        log.info("settlement.scheduler.triggered date={}", today);
        SettlementJobResult result = settlementJobService.runSettlementJob(today);
        log.info("settlement.scheduler.done date={} settled={} total={} file={}",
                today, result.settledCount(), result.totalAmount(), result.fileName());
    }
}
