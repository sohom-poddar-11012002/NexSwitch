package com.nexswitch.qa.arch;

import com.tngtech.archunit.junit.AnalyzeClasses;
import com.tngtech.archunit.junit.ArchTest;
import com.tngtech.archunit.lang.ArchRule;

import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.classes;
import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noClasses;
import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noFields;

// LEARN: ArchUnit — static bytecode analysis that runs as a JUnit test. Rules are evaluated at
//        build time against compiled classes, not at runtime. Violations fail CI like a broken test.
@AnalyzeClasses(packages = "com.nexswitch.qa")
class QaArchitectureTest {

    // Rule 1: QA domain must be pure Java — zero Spring, JPA, Kafka, jPOS
    @ArchTest
    static final ArchRule qaDomainMustHaveNoInfrastructureDependencies =
        noClasses()
            .that().resideInAPackage("com.nexswitch.qa.domain..")
            .should().dependOnClassesThat()
            .resideInAnyPackage(
                "org.springframework..",
                "jakarta.persistence..",
                "org.apache.kafka..",
                "org.jpos..",
                "org.hibernate.."
            )
            .because("QA domain is a pure Java bounded context — same constraint as production domain");

    // Rule 2: QA domain must not import production domain classes
    @ArchTest
    static final ArchRule qaDomainMustNotImportProductionDomain =
        noClasses()
            .that().resideInAPackage("com.nexswitch.qa.domain..")
            .should().dependOnClassesThat()
            .resideInAPackage("com.nexswitch.domain..")
            .because("QA test infrastructure must be independent of production internals — coupling causes fragile tests");

    // Rule 3: No @Autowired field injection in QA codebase
    @ArchTest
    static final ArchRule noFieldInjection =
        noFields()
            .should().beAnnotatedWith("org.springframework.beans.factory.annotation.Autowired")
            .because("Constructor injection only — all dependencies must be final");

    // Rule 4: No @Value field injection — constructor parameter injection only
    @ArchTest
    static final ArchRule noValueFieldInjection =
        noFields()
            .should().beAnnotatedWith("org.springframework.beans.factory.annotation.Value")
            .because("@Value on fields is untestable — inject via constructor parameters instead");

    // Rule 5: Channel adapters must implement TestChannelPort and reside in adapter.channel package
    @ArchTest
    static final ArchRule channelAdaptersMustImplementPort =
        classes()
            .that().resideInAPackage("com.nexswitch.qa.adapter.channel..")
            .and().haveNameMatching(".*Adapter")
            .should().implement("com.nexswitch.qa.domain.port.outbound.TestChannelPort")
            .because("All channel adapters are discovered via TestChannelPort.supports() — non-implementing classes are dead code");

    // Rule 6: REST controllers must not call domain services directly — only use case ports
    @ArchTest
    static final ArchRule restControllersMustUsePorts =
        noClasses()
            .that().resideInAPackage("com.nexswitch.qa.rest..")
            .should().dependOnClassesThat()
            .resideInAPackage("com.nexswitch.qa.domain.service..")
            .because("REST controllers are inbound adapters — they call use case ports, never domain service implementations");

    // Rule 7: Application layer must not depend on adapter implementations directly
    @ArchTest
    static final ArchRule applicationLayerMustNotDependOnAdapterImpl =
        noClasses()
            .that().resideInAPackage("com.nexswitch.qa.application..")
            .and().haveNameNotMatching("QaOrchestratorConfig")  // config wires beans — exempt
            .should().dependOnClassesThat()
            .resideInAnyPackage(
                "com.nexswitch.qa.adapter.channel..",
                "com.nexswitch.qa.adapter.persistence..",
                "com.nexswitch.qa.adapter.repository.."
            )
            .because("Application services depend on ports (interfaces), not adapter implementations");

    // Rule 8: Domain ports must be interfaces only
    @ArchTest
    static final ArchRule domainPortsMustBeInterfaces =
        classes()
            .that().resideInAPackage("com.nexswitch.qa.domain.port.outbound..")
            .and().haveNameMatching(".*Port|.*Repository")
            .should().beInterfaces()
            .because("Outbound ports are contracts — adapters implement them, domain defines them");

    // Rule 9: Inbound use case ports must be interfaces
    @ArchTest
    static final ArchRule inboundUseCaseMustBeInterfaces =
        classes()
            .that().resideInAPackage("com.nexswitch.qa.domain.port.inbound..")
            .and().haveNameMatching(".*UseCase")
            .should().beInterfaces()
            .because("Inbound use case ports decouple REST controllers from application service implementations");
}
