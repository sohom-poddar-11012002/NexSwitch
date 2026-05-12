package com.payments.domain.arch;

import com.tngtech.archunit.junit.AnalyzeClasses;
import com.tngtech.archunit.junit.ArchTest;
import com.tngtech.archunit.lang.ArchRule;

import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.classes;
import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noClasses;

@AnalyzeClasses(packages = "com.payments.domain")
class PortsArchitectureTest {

    @ArchTest
    static final ArchRule inboundUseCasesMustBeInterfaces =
        classes()
            .that().resideInAPackage("com.payments.domain.port.inbound..")
            .and().haveNameMatching(".*UseCase")
            .should().beInterfaces()
            .because("Inbound ports are use case interfaces — the application drives them, never the domain");

    @ArchTest
    static final ArchRule outboundPortsMustBeInterfaces =
        classes()
            .that().resideInAPackage("com.payments.domain.port.outbound..")
            .and().haveNameMatching(".*Port|.*Repository")
            .should().beInterfaces()
            .because("Outbound ports are infrastructure abstractions — adapters implement them, domain defines them");

    @ArchTest
    static final ArchRule commandsMustBeRecords =
        classes()
            .that().resideInAPackage("com.payments.domain.port.inbound..")
            .and().haveNameMatching(".*Command")
            .should().beRecords()
            .because("Commands are immutable value objects — records enforce this structurally");

    @ArchTest
    static final ArchRule portsMustNotDependOnAdapters =
        noClasses()
            .that().resideInAPackage("com.payments.domain.port..")
            .should().dependOnClassesThat()
            .resideInAnyPackage(
                "com.payments.adapters..",
                "org.springframework..",
                "jakarta.persistence..",
                "org.apache.kafka..",
                "org.jpos..",
                "software.amazon.."
            )
            .because("Ports are domain contracts — they must have zero infrastructure dependencies");
}
