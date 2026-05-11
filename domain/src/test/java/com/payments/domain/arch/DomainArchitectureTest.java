package com.payments.domain.arch;

import com.tngtech.archunit.junit.AnalyzeClasses;
import com.tngtech.archunit.junit.ArchTest;
import com.tngtech.archunit.lang.ArchRule;

import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.classes;
import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noClasses;
import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noFields;
import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noMethods;

@AnalyzeClasses(packages = "com.payments")
class DomainArchitectureTest {

    // Rule 1: Domain must not depend on adapters, Spring, JPA, Kafka, or jPOS
    @ArchTest
    static final ArchRule domainMustHaveNoInfrastructureDependencies =
        noClasses()
            .that().resideInAPackage("com.payments.domain..")
            .should().dependOnClassesThat()
            .resideInAnyPackage(
                "com.payments.adapters..",
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
            .resideInAPackage("com.payments.adapters.outbound..")
            .because("Controllers are inbound adapters and must not depend on outbound adapter implementations");

    // Rule 4: Domain services must only depend on domain types and domain ports
    @ArchTest
    static final ArchRule domainServicesMustOnlyUseDomainTypes =
        classes()
            .that().resideInAPackage("com.payments.domain.service..")
            .should().onlyDependOnClassesThat()
            .resideInAnyPackage(
                "com.payments.domain..",
                "java..",
                "javax.."
            )
            .because("Domain services are pure Java — they orchestrate through ports, never touch infrastructure directly");

    // Rule 5: JPA entities must not leak into the domain layer
    @ArchTest
    static final ArchRule domainMustNotUseJpaEntities =
        noClasses()
            .that().resideInAPackage("com.payments.domain..")
            .should().dependOnClassesThat()
            .resideInAPackage("com.payments.adapters.outbound.persistence.entity..")
            .because("Domain records are pure. JPA entities are adapter concerns. TransactionMapper is the only bridge.");

    // Rule 6: Adapters must not bypass ports to call domain services directly
    @ArchTest
    static final ArchRule adaptersMustCallDomainThroughPorts =
        noClasses()
            .that().resideInAPackage("com.payments.adapters..")
            .should().dependOnClassesThat()
            .resideInAPackage("com.payments.domain.service..")
            .because("Adapters must call inbound use case ports, never domain service implementations directly");
}
