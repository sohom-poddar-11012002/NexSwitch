package com.nexswitch.settlement.scheduler;

import com.nexswitch.settlement.service.PayoutJobResult;
import com.nexswitch.settlement.service.PayoutJobService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.Clock;
import java.time.LocalDate;

@Component
public class PayoutScheduler {

    private static final Logger log = LoggerFactory.getLogger(PayoutScheduler.class);

    private final PayoutJobService payoutJobService;
    private final Clock clock;

    public PayoutScheduler(PayoutJobService payoutJobService, Clock clock) {
        this.payoutJobService = payoutJobService;
        this.clock            = clock;
    }

    @Scheduled(cron = "0 0 9 * * *", zone = "Asia/Kolkata")
    public void runDailyPayout() {
        LocalDate today = LocalDate.now(clock);
        log.info("payout.scheduler.triggered date={}", today);
        PayoutJobResult result = payoutJobService.runPayoutJob(today);
        log.info("payout.scheduler.done date={} merchants={} total={}",
                today, result.merchantCount(), result.totalPayout());
    }
}
