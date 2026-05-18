package com.nexswitch.qa.application;

import com.nexswitch.qa.domain.model.TestSuite;
import com.nexswitch.qa.domain.port.inbound.TriggerSuiteUseCase;
import com.nexswitch.qa.domain.port.outbound.NotificationPort;
import com.nexswitch.qa.domain.port.outbound.ScenarioRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.SchedulingConfigurer;
import org.springframework.scheduling.config.ScheduledTaskRegistrar;
import org.springframework.scheduling.support.CronTrigger;
import org.springframework.stereotype.Component;

import java.util.Map;

// LEARN: SchedulingConfigurer — Spring hook that fires before the first scheduled task runs.
//        Unlike @Scheduled which requires compile-time cron strings, configureTasks() reads
//        cron expressions from runtime state (YAML/DB) and registers them dynamically.
@Component
public class SuiteScheduler implements SchedulingConfigurer {

    private static final Logger log = LoggerFactory.getLogger(SuiteScheduler.class);

    private final ScenarioRepository  suiteRepo;
    private final TriggerSuiteUseCase triggerSuite;
    private final NotificationPort    notifier;

    public SuiteScheduler(ScenarioRepository suiteRepo,
                          TriggerSuiteUseCase triggerSuite,
                          NotificationPort notifier) {
        this.suiteRepo   = suiteRepo;
        this.triggerSuite = triggerSuite;
        this.notifier    = notifier;
    }

    @Override
    public void configureTasks(ScheduledTaskRegistrar registrar) {
        for (TestSuite suite : suiteRepo.findAllSuites()) {
            if (suite.schedule() == null || suite.schedule().isBlank()) continue;
            registrar.addTriggerTask(
                    () -> runScheduledSuite(suite),
                    triggerContext -> new CronTrigger(suite.schedule()).nextExecution(triggerContext)
            );
            log.info("qa.scheduler.registered suite_id={} cron={}", suite.id(), suite.schedule());
        }
    }

    private void runScheduledSuite(TestSuite suite) {
        try {
            triggerSuite.trigger(new TriggerSuiteUseCase.TriggerSuiteCommand(suite.id(), Map.of()));
            log.info("qa.scheduler.triggered suite_id={}", suite.id());
        } catch (Exception e) {
            log.error("qa.scheduler.trigger_failed suite_id={} err={}", suite.id(), e.getMessage());
            notifier.notify("Suite schedule failed: " + suite.name(), e.getMessage());
        }
    }
}
