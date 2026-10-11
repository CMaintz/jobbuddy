package com.autoapplicant;

import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.classes;
import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noClasses;

import com.tngtech.archunit.core.domain.JavaAccess;
import com.tngtech.archunit.core.domain.JavaClass;
import com.tngtech.archunit.core.domain.JavaCodeUnit;
import com.tngtech.archunit.core.domain.JavaField;
import com.tngtech.archunit.core.domain.JavaType;
import com.tngtech.archunit.core.importer.ImportOption;
import com.tngtech.archunit.junit.AnalyzeClasses;
import com.tngtech.archunit.junit.ArchTest;
import com.tngtech.archunit.lang.ArchCondition;
import com.tngtech.archunit.lang.ArchRule;
import com.tngtech.archunit.lang.ConditionEvents;
import com.tngtech.archunit.lang.SimpleConditionEvent;

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

    private static final String PORT_IN_PACKAGE = "com.autoapplicant.port.in";
    private static final String USECASE_PACKAGE = "com.autoapplicant.usecase";

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

    /**
     * A use case may implement its inbound ports and use port.in records (commands, results), but
     * it may not hold or call another use case, through a port or a class. That includes another
     * implementation of a port it implements itself. Shared logic belongs in a helper that is not
     * a use case; composing use cases belongs in the adapter.
     */
    @ArchTest
    static final ArchRule use_cases_never_call_other_use_cases = classes()
            .that().resideInAPackage(USECASE)
            .should(notReferenceAnotherUseCase())
            .because("a use case composes outbound ports and plain helpers; orchestrating several "
                    + "use cases belongs in the adapter that drives them");

    /**
     * An outbound port implemented in usecase lets one use case reach another through port.out,
     * past the rule above. Outbound ports are implemented by adapters.
     */
    @ArchTest
    static final ArchRule use_cases_never_implement_outbound_ports = noClasses()
            .that().resideInAPackage(USECASE)
            .should().implement(JavaClass.Predicates.resideInAPackage(PORT_OUT))
            .because("port.out is what the application needs from infrastructure; adapters provide it");

    @ArchTest
    static final ArchRule adapters_call_use_cases_through_inbound_ports = noClasses()
            .that().resideInAPackage(ADAPTER)
            .should().dependOnClassesThat().resideInAPackage(USECASE)
            .because("adapters depend on port.in interfaces, never on concrete use-case classes");

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

    /**
     * Looks at what a use case holds, receives and calls, not at what it implements: implementing
     * a port is the point, while holding one (even the same one) means calling someone else.
     */
    private static ArchCondition<JavaClass> notReferenceAnotherUseCase() {
        return new ArchCondition<>("not hold, receive or call an inbound port or another use case") {
            @Override
            public void check(JavaClass useCase, ConditionEvents events) {
                for (JavaField field : useCase.getFields()) {
                    reportUseCasesIn(useCase, field.getType(), field.getFullName(), events);
                }
                for (JavaCodeUnit codeUnit : useCase.getCodeUnits()) {
                    for (JavaType parameter : codeUnit.getParameterTypes()) {
                        reportUseCasesIn(useCase, parameter, codeUnit.getFullName(), events);
                    }
                }
                for (JavaAccess<?> access : useCase.getAccessesFromSelf()) {
                    if (isOtherUseCase(useCase, access.getTargetOwner())) {
                        events.add(SimpleConditionEvent.violated(access, access.getDescription()));
                    }
                }
            }
        };
    }

    private static void reportUseCasesIn(JavaClass useCase, JavaType type, String where,
                                         ConditionEvents events) {
        for (JavaClass involved : type.getAllInvolvedRawTypes()) {
            if (isOtherUseCase(useCase, involved)) {
                events.add(SimpleConditionEvent.violated(useCase, where + " uses " + involved.getName()));
            }
        }
    }

    /** A nested class reaching its enclosing class is the same use case talking to itself. */
    private static boolean isOtherUseCase(JavaClass useCase, JavaClass target) {
        return isUseCaseEntryPoint(target) && !topLevelName(useCase).equals(topLevelName(target));
    }

    /** An inbound port interface, or a class in usecase that implements one. */
    private static boolean isUseCaseEntryPoint(JavaClass target) {
        if (target.isInterface() && isIn(target, PORT_IN_PACKAGE)) {
            return true;
        }
        return isIn(target, USECASE_PACKAGE)
                && target.getAllRawInterfaces().stream().anyMatch(i -> isIn(i, PORT_IN_PACKAGE));
    }

    private static boolean isIn(JavaClass javaClass, String packagePrefix) {
        String packageName = javaClass.getPackageName();
        return packageName.equals(packagePrefix) || packageName.startsWith(packagePrefix + ".");
    }

    private static String topLevelName(JavaClass javaClass) {
        String name = javaClass.getName();
        int nested = name.indexOf('$');
        return nested < 0 ? name : name.substring(0, nested);
    }
}
