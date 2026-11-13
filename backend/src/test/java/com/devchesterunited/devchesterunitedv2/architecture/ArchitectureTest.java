package com.devchesterunited.devchesterunitedv2.architecture;

import com.tngtech.archunit.core.domain.JavaClasses;
import com.tngtech.archunit.core.importer.ClassFileImporter;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import com.tngtech.archunit.library.dependencies.SlicesRuleDefinition;

import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.classes;
import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noClasses;
import static com.tngtech.archunit.core.importer.ImportOption.Predefined.DO_NOT_INCLUDE_TESTS;


class ArchitectureTest {

    private static final String BASE_PACKAGE =
            "com.devchesterunited.devchesterunitedv2";

    private static JavaClasses clases;

    @BeforeAll
    static void cargarClases() {
        clases = new ClassFileImporter()
                .withImportOption(DO_NOT_INCLUDE_TESTS)
                .importPackages(BASE_PACKAGE);
    }

    @Test
    void persistenciaNoDebeDependerDeControllers() {
        noClasses()
                .that()
                .resideInAPackage("..persistence..")
                .should()
                .dependOnClassesThat()
                .resideInAPackage("..controller..")
                .check(clases);
    }

    @Test
    void servicesNoDebenDependerDeControllers() {
        noClasses()
                .that()
                .resideInAPackage("..service..")
                .should()
                .dependOnClassesThat()
                .resideInAPackage("..controller..")
                .check(clases);
    }

    @Test
    void controllersRestDebenEstarEnPaqueteController() {
        classes()
                .that()
                .areAnnotatedWith(
                        org.springframework.web.bind.annotation.RestController.class)
                .should()
                .resideInAPackage("..controller..")
                .check(clases);
    }

    @Test
    void modeloNoDebeDependerDeControllersNiPersistencia() {
        noClasses()
                .that()
                .resideInAPackage("..modelo..")
                .should()
                .dependOnClassesThat()
                .resideInAnyPackage(
                        "..controller..",
                        "..persistence.."
                )
                .check(clases);
    }

    @Test
    void controllersNoDebenAccederDirectamenteARepositories() {
        noClasses()
                .that()
                .resideInAPackage("..controller..")
                .should()
                .dependOnClassesThat()
                .resideInAPackage("..persistence..repository..")
                .check(clases);
    }

    /*
    @Test
    void paquetesPrincipalesNoDebenTenerDependenciasCirculares() {
        SlicesRuleDefinition.slices()
                .matching("com.devchesterunited.devchesterunitedv2.(*)..")
                .should()
                .beFreeOfCycles()
                .check(clases);
    }
    */

    @Test
    void interfacesDeServiciosNoDebenDependerDeImplementaciones() {
        noClasses()
                .that()
                .resideInAPackage("..service.interfaces..")
                .should()
                .dependOnClassesThat()
                .resideInAPackage("..service.impl..")
                .check(clases);
    }
}
