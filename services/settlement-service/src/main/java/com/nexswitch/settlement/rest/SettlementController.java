package com.nexswitch.settlement.rest;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.batch.core.Job;
import org.springframework.batch.core.JobParameters;
import org.springframework.batch.core.JobParametersBuilder;
import org.springframework.batch.core.launch.JobLauncher;
import org.springframework.http.ResponseEntity;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

// LEARN: @Scheduled + REST trigger — nightly cron handles production; the POST endpoint
//        allows ops to re-run settlement on demand without restarting the service.
@RestController
@RequestMapping("/settlement")
public class SettlementController {

    private static final Logger log = LoggerFactory.getLogger(SettlementController.class);

    private final JobLauncher jobLauncher;
    private final Job settlementJob;

    public SettlementController(JobLauncher jobLauncher, Job settlementJob) {
        this.jobLauncher = jobLauncher;
        this.settlementJob = settlementJob;
    }

    @PostMapping("/run")
    public ResponseEntity<String> trigger() throws Exception {
        return launchJob();
    }

    @Scheduled(cron = "0 30 23 * * *")
    public void scheduledRun() throws Exception {
        launchJob();
    }

    private ResponseEntity<String> launchJob() throws Exception {
        JobParameters params = new JobParametersBuilder()
                .addLong("run.id", System.currentTimeMillis())
                .toJobParameters();
        log.info("settlement.batch.launching");
        jobLauncher.run(settlementJob, params);
        return ResponseEntity.ok("settlement job launched");
    }
}
