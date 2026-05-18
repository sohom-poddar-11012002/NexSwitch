package com.nexswitch.qa.application;

import com.nexswitch.qa.domain.model.TestSuite;
import com.nexswitch.qa.domain.port.inbound.TriggerSuiteUseCase;
import com.nexswitch.qa.domain.port.outbound.NotificationPort;
import com.nexswitch.qa.domain.port.outbound.ScenarioRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.scheduling.config.ScheduledTaskRegistrar;

import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class SuiteSchedulerTest {

    @Mock ScenarioRepository  suiteRepo;
    @Mock TriggerSuiteUseCase triggerSuite;
    @Mock NotificationPort    notifier;

    SuiteScheduler scheduler;

    @BeforeEach
    void setUp() {
        scheduler = new SuiteScheduler(suiteRepo, triggerSuite, notifier);
    }

    @Test
    void configureTasks_registersCronTaskForScheduledSuite() {
        when(suiteRepo.findAllSuites()).thenReturn(List.of(
                suite("nightly", "0 2 * * *"),
                suite("no-schedule", null)
        ));

        CapturingRegistrar registrar = new CapturingRegistrar();
        scheduler.configureTasks(registrar);

        assertThat(registrar.taskCount).isEqualTo(1);
    }

    @Test
    void configureTasks_skipsBlankSchedule() {
        when(suiteRepo.findAllSuites()).thenReturn(List.of(
                suite("suite-a", ""),
                suite("suite-b", "   ")
        ));

        CapturingRegistrar registrar = new CapturingRegistrar();
        scheduler.configureTasks(registrar);

        assertThat(registrar.taskCount).isZero();
    }

    @Test
    void configureTasks_noSuites_registersNothing() {
        when(suiteRepo.findAllSuites()).thenReturn(List.of());

        CapturingRegistrar registrar = new CapturingRegistrar();
        scheduler.configureTasks(registrar);

        assertThat(registrar.taskCount).isZero();
    }

    @Test
    void onTriggerFailure_notifierIsCalled() {
        TestSuite suite = suite("failing-suite", "0 3 * * *");
        when(suiteRepo.findAllSuites()).thenReturn(List.of(suite));
        when(triggerSuite.trigger(any())).thenThrow(new RuntimeException("Suite not found"));

        CapturingRegistrar registrar = new CapturingRegistrar();
        scheduler.configureTasks(registrar);
        registrar.runCapturedTask();

        verify(notifier).notify(contains("failing-suite"), contains("Suite not found"));
    }

    @Test
    void onSuccessfulTrigger_notifierNotCalled() {
        TestSuite suite = suite("ok-suite", "0 4 * * *");
        when(suiteRepo.findAllSuites()).thenReturn(List.of(suite));

        CapturingRegistrar registrar = new CapturingRegistrar();
        scheduler.configureTasks(registrar);
        registrar.runCapturedTask();

        verify(notifier, never()).notify(any(), any());
    }

    // ── helpers ───────────────────────────────────────────────────────────────

    private TestSuite suite(String id, String schedule) {
        return new TestSuite(id, id + "-name", List.of("run-a"), TestSuite.ExecutionMode.SEQUENTIAL,
                1, Map.of(), TestSuite.OnFailure.FAIL_FAST, schedule);
    }

    static class CapturingRegistrar extends ScheduledTaskRegistrar {
        int      taskCount = 0;
        Runnable lastTask  = null;

        @Override
        public void addTriggerTask(Runnable task, org.springframework.scheduling.Trigger trigger) {
            taskCount++;
            lastTask = task;
        }

        void runCapturedTask() {
            if (lastTask != null) lastTask.run();
        }
    }
}
