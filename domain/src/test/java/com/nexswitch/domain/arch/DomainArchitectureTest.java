package com.nexswitch.domain.arch;

import com.tngtech.archunit.junit.AnalyzeClasses;
import com.tngtech.archunit.junit.ArchTest;
import com.tngtech.archunit.lang.ArchRule;

import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.classes;
import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noClasses;
import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noFields;
import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noMethods;

@AnalyzeClasses(packages = "com.nexswitch")
class DomainArchitectureTest {

    // Rule 1: Domain must not depend on adapters, Spring, JPA, Kafka, or jPOS
    @ArchTest
    static final ArchRule domainMustHaveNoInfrastructureDependencies =
        noClasses()
            .that().resideInAPackage("com.nexswitch.domain..")
            .should().dependOnClassesThat()
            .resideInAnyPackage(
                "com.nexswitch.adapters..",
                "org.springframework..",
                "jakarta.persistence..",
                "org.apache.kafka..",
                "org.jpos..",
                "software.amazon..",
                "redis..",
                "org.hibernate.."
            )
            .because("Domain is the core of hexagonal architecture and must have zero infrastructure dependencies");

    // Rule 2: No @Autowired field injection anywhere in the codebase
    @ArchTest
    static final ArchRule noFieldInjection =
        noFields()
            .should().beAnnotatedWith("org.springframework.beans.factory.annotation.Autowired")
            .because("Constructor injection only — all dependencies must be final and injected via constructors");

    // Rule 3: Controllers may only depend on use case ports and domain models, not adapters directly
    @ArchTest
    static final ArchRule controllersMustNotDependOnAdapters =
        noClasses()
            .that().haveNameMatching(".*Controller")
            .should().dependOnClassesThat()
            .resideInAPackage("com.nexswitch.adapters.outbound..")
            .because("Controllers are inbound adapters and must not depend on outbound adapter implementations");

    // Rule 4: Domain services must only depend on domain types and domain ports
    @ArchTest
    static final ArchRule domainServicesMustOnlyUseDomainTypes =
        classes()
            .that().resideInAPackage("com.nexswitch.domain.service..")
            .and().haveNameNotMatching(".*Test")
            .should().onlyDependOnClassesThat()
            .resideInAnyPackage(
                "com.nexswitch.domain..",
                "java..",
                "javax.."
            )
            .because("Domain services are pure Java — they orchestrate through ports, never touch infrastructure directly");

    // Rule 5: JPA entities must not leak into the domain layer
    @ArchTest
    static final ArchRule domainMustNotUseJpaEntities =
        noClasses()
            .that().resideInAPackage("com.nexswitch.domain..")
            .should().dependOnClassesThat()
            .resideInAPackage("com.nexswitch.adapters.outbound.persistence.entity..")
            .because("Domain records are pure. JPA entities are adapter concerns. TransactionMapper is the only bridge.");

    // Rule 6: Adapters must not bypass ports to call domain services directly
    @ArchTest
    static final ArchRule adaptersMustCallDomainThroughPorts =
        noClasses()
            .that().resideInAPackage("com.nexswitch.adapters..")
            .should().dependOnClassesThat()
            .resideInAPackage("com.nexswitch.domain.service..")
            .because("Adapters must call inbound use case ports, never domain service implementations directly");

    // Rule 7: QA domain must not import production domain — test infrastructure must stay isolated
    @ArchTest
    static final ArchRule qaDomainMustNotImportProductionDomain =
        noClasses()
            .that().resideInAPackage("com.nexswitch.qa.domain..")
            .should().dependOnClassesThat()
            .resideInAPackage("com.nexswitch.domain..")
            .because("QA domain is a fully isolated bounded context — coupling to production domain makes tests fragile");

    // Rule 8: QA domain services must have zero Spring dependencies (pure Java like production domain)
    @ArchTest
    static final ArchRule qaDomainServicesMustBeSpringFree =
        noClasses()
            .that().resideInAPackage("com.nexswitch.qa.domain.service..")
            .should().dependOnClassesThat()
            .resideInAnyPackage(
                "org.springframework.beans..",
                "org.springframework.context..",
                "jakarta.persistence..",
                "org.apache.kafka.."
            )
            .because("QA domain services are pure Java — same constraint as production domain services");

    // Rule 9: Application-layer @Service beans must not contain business logic inline —
    //         they must delegate to a domain service or use case. Checked by ensuring they
    //         do not call repository or adapter methods directly (only through injected ports).
    @ArchTest
    static final ArchRule applicationServicesMustNotCallRepositoriesDirectly =
        noClasses()
            .that().resideInAPackage("com.nexswitch.app..")
            .should().dependOnClassesThat()
            .resideInAnyPackage(
                "com.nexswitch.adapters.outbound..",
                "jakarta.persistence.."
            )
            .because("Application layer wires Spring beans only — business logic and data access belong in domain and adapters respectively");

    // Rule 10: No @Value field injection — @Value must be on constructor parameters only
    @ArchTest
    static final ArchRule noValueFieldInjection =
        noFields()
            .should().beAnnotatedWith("org.springframework.beans.factory.annotation.Value")
            .because("@Value on fields bypasses constructor injection and makes beans harder to test — use @Value on constructor parameters instead");
}
