package com.nexswitch.qa.domain.port.outbound;

import com.nexswitch.qa.domain.model.TestRun;
import com.nexswitch.qa.domain.model.TestScenario;
import com.nexswitch.qa.domain.model.TestSuite;

import java.util.List;
import java.util.Optional;

public interface ScenarioRepository {

    List<TestScenario> findAllScenarios();

    Optional<TestScenario> findScenarioById(String id);

    List<TestScenario> findScenariosByCategory(String category);

    Optional<TestRun> findRunById(String id);

    List<TestRun> findAllRuns();

    Optional<TestSuite> findSuiteById(String id);

    List<TestSuite> findAllSuites();

    void reload();
}
