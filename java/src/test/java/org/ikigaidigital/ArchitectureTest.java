package org.ikigaidigital;

import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noClasses;

import com.tngtech.archunit.core.importer.ImportOption;
import com.tngtech.archunit.junit.AnalyzeClasses;
import com.tngtech.archunit.junit.ArchTest;
import com.tngtech.archunit.lang.ArchRule;

/** Hexagonal layering: the core (domain, application) knows nothing about frameworks or adapters. */
@AnalyzeClasses(packages = "org.ikigaidigital", importOptions = ImportOption.DoNotIncludeTests.class)
class ArchitectureTest {

    private static final String DOMAIN = "org.ikigaidigital.domain..";
    private static final String APPLICATION = "org.ikigaidigital.application..";
    private static final String ADAPTER = "org.ikigaidigital.adapter..";
    private static final String[] FRAMEWORKS = {
        "org.springframework..",
        "jakarta.persistence..",
        "org.hibernate..",
        "com.fasterxml.jackson..",
        "tools.jackson.."
    };

    @ArchTest
    static final ArchRule domainIsFrameworkFree = noClasses()
            .that()
            .resideInAPackage(DOMAIN)
            .should()
            .dependOnClassesThat()
            .resideInAnyPackage(FRAMEWORKS)
            .allowEmptyShould(true);

    @ArchTest
    static final ArchRule domainDoesNotDependOnOuterLayers = noClasses()
            .that()
            .resideInAPackage(DOMAIN)
            .should()
            .dependOnClassesThat()
            .resideInAnyPackage(APPLICATION, ADAPTER)
            .allowEmptyShould(true);

    @ArchTest
    static final ArchRule applicationIsFrameworkFree = noClasses()
            .that()
            .resideInAPackage(APPLICATION)
            .should()
            .dependOnClassesThat()
            .resideInAnyPackage(FRAMEWORKS)
            .allowEmptyShould(true);

    @ArchTest
    static final ArchRule applicationDoesNotDependOnAdapters = noClasses()
            .that()
            .resideInAPackage(APPLICATION)
            .should()
            .dependOnClassesThat()
            .resideInAPackage(ADAPTER)
            .allowEmptyShould(true);

    @ArchTest
    static final ArchRule inboundAdaptersDoNotUseOutboundAdapters = noClasses()
            .that()
            .resideInAPackage("org.ikigaidigital.adapter.in..")
            .should()
            .dependOnClassesThat()
            .resideInAPackage("org.ikigaidigital.adapter.out..")
            .allowEmptyShould(true);

    @ArchTest
    static final ArchRule outboundAdaptersDoNotUseInboundAdapters = noClasses()
            .that()
            .resideInAPackage("org.ikigaidigital.adapter.out..")
            .should()
            .dependOnClassesThat()
            .resideInAPackage("org.ikigaidigital.adapter.in..")
            .allowEmptyShould(true);
}
