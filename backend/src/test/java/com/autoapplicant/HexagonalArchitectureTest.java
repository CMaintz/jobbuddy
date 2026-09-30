package com.autoapplicant;

import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noClasses;

import com.tngtech.archunit.core.importer.ImportOption;
import com.tngtech.archunit.junit.AnalyzeClasses;
import com.tngtech.archunit.junit.ArchTest;
import com.tngtech.archunit.lang.ArchRule;

/** Enforces the ports-and-adapters layering described in the README and CLAUDE.md. */
@AnalyzeClasses(packages = "com.autoapplicant", importOptions = ImportOption.DoNotIncludeTests.class)
class HexagonalArchitectureTest {

    private static final String DOMAIN = "com.autoapplicant.domain..";
    private static final String PORT = "com.autoapplicant.port..";
    private static final String PORT_OUT = "com.autoapplicant.port.out..";
    private static final String USECASE = "com.autoapplicant.usecase..";
    private static final String ADAPTER = "com.autoapplicant.adapter..";
    private static final String CONTROLLER = "com.autoapplicant.adapter.web.controller..";
    private static final String CONFIG = "com.autoapplicant.config..";

    @ArchTest
    static final ArchRule domain_is_pure = noClasses()
            .that().resideInAPackage(DOMAIN)
            .should().dependOnClassesThat()
            .resideInAnyPackage(PORT, USECASE, ADAPTER, CONFIG, "org.springframework..", "jakarta.persistence..")
            .because("domain holds plain records and value objects with no framework or layer dependencies");

    @ArchTest
    static final ArchRule ports_depend_only_on_the_domain = noClasses()
            .that().resideInAPackage(PORT)
            .should().dependOnClassesThat().resideInAnyPackage(USECASE, ADAPTER, CONFIG)
            .because("ports are the boundary contracts; implementations live on either side of them");

    @ArchTest
    static final ArchRule use_cases_never_touch_adapters = noClasses()
            .that().resideInAPackage(USECASE)
            .should().dependOnClassesThat().resideInAPackage(ADAPTER)
            .because("use cases reach infrastructure only through port.out interfaces");

    @ArchTest
    static final ArchRule controllers_call_use_cases_through_inbound_ports = noClasses()
            .that().resideInAPackage(CONTROLLER)
            .should().dependOnClassesThat().resideInAPackage(USECASE)
            .because("controllers depend on port.in interfaces, never on concrete use-case classes");

    /**
     * Two controllers still reach an outbound port directly; they are listed so the
     * rule holds for every other controller and the debt stays visible.
     */
    @ArchTest
    static final ArchRule controllers_do_not_bypass_use_cases = noClasses()
            .that().resideInAPackage(CONTROLLER)
            .and().doNotHaveFullyQualifiedName("com.autoapplicant.adapter.web.controller.AiController")
            .and().doNotHaveFullyQualifiedName("com.autoapplicant.adapter.web.controller.CrawlerAdminController")
            .should().dependOnClassesThat().resideInAPackage(PORT_OUT)
            .because("a controller that talks to an outbound port skips the application layer");

    @ArchTest
    static final ArchRule the_core_never_depends_on_spring_web = noClasses()
            .that().resideInAnyPackage(USECASE, PORT, DOMAIN)
            .should().dependOnClassesThat().resideInAnyPackage("org.springframework.web..", "jakarta.servlet..")
            .because("HTTP is an adapter concern");
}
