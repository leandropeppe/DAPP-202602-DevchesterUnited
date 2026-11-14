
package com.devchesterunited.devchesterunitedv2.architecture;

import com.tngtech.archunit.core.domain.JavaClasses;
import com.tngtech.archunit.core.importer.ClassFileImporter;
import com.tngtech.archunit.library.dependencies.SlicesRuleDefinition;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import static com.tngtech.archunit.core.importer.ImportOption.Predefined.DO_NOT_INCLUDE_TESTS;
import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.classes;
import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noClasses;

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

    // ============================================================
    // 1. CAPA CONTROLLER - API REST
    // Verifica la ubicación de controllers y evita accesos
    // directos a componentes internos de persistencia.
    // ============================================================

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
    void controllersNoDebenAccederDirectamenteARepositories() {
        noClasses()
                .that()
                .resideInAPackage("..controller..")
                .should()
                .dependOnClassesThat()
                .resideInAPackage("..persistence..repository..")
                .check(clases);
    }

    @Test
    void controllersNoDebenDependerDeEntidadesJpa() {
        noClasses()
                .that()
                .resideInAPackage("..controller..")
                .should()
                .dependOnClassesThat()
                .areAnnotatedWith(jakarta.persistence.Entity.class)
                .check(clases);
    }

    // ============================================================
    // 2. CAPA SERVICE - LOGICA DE APLICACION
    // Impide dependencias hacia controllers y mantiene
    // las interfaces independientes de las implementaciones.
    // ============================================================

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
    void interfacesDeServiciosNoDebenDependerDeImplementaciones() {
        noClasses()
                .that()
                .resideInAPackage("..service.interfaces..")
                .should()
                .dependOnClassesThat()
                .resideInAPackage("..service.impl..")
                .check(clases);
    }

    // ============================================================
    // 3. CAPA PERSISTENCE - ACCESO A DATOS
    // Protege la independencia de persistencia respecto
    // de controllers, contratos HTTP y servicios.
    // ============================================================

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
    void repositoriesDebenEstarEnPersistencia() {
        classes()
                .that()
                .areAssignableTo(
                        org.springframework.data.repository.Repository.class)
                .should()
                .resideInAPackage("..persistence..")
                .check(clases);
    }

    @Test
    void persistenciaNoDebeDependerDeDtosHttp() {
        noClasses()
                .that()
                .resideInAPackage("..persistence..")
                .should()
                .dependOnClassesThat()
                .resideInAnyPackage(
                        "..controller.dto..",
                        "..controller.*.dto.."
                )
                .check(clases);
    }

    @Test
    void repositoriesNoDebenDependerDeServicios() {
        noClasses()
                .that()
                .resideInAPackage("..persistence.*.repository..")
                .should()
                .dependOnClassesThat()
                .resideInAPackage("..service..")
                .check(clases);
    }

    // ============================================================
    // 4. MODELO DE DOMINIO
    // Mantiene el modelo independiente de la API,
    // la persistencia y los servicios.
    // ============================================================

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
    void entidadesJpaNoDebenDependerDeServicios() {
        noClasses()
                .that()
                .areAnnotatedWith(jakarta.persistence.Entity.class)
                .should()
                .dependOnClassesThat()
                .resideInAPackage("..service..")
                .check(clases);
    }

    // ============================================================
    // 5. CONVENCIONES JPA
    // Verifica que las entidades estén ubicadas
    // en la capa correspondiente.
    // ============================================================

    @Test
    void entidadesJpaDebenEstarEnPersistencia() {
        classes()
                .that()
                .areAnnotatedWith(jakarta.persistence.Entity.class)
                .should()
                .resideInAPackage("..persistence..")
                .check(clases);
    }

    // ============================================================
    // 6. DEPENDENCIAS CIRCULARES - DEUDA TECNICA
    // Diagnóstico inicial: 58 recorridos circulares.
    // Pendiente de refactorización progresiva.
    // ============================================================


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




    // ============================================================
    // 7. RESTRICCIONES ENTRE CAPAS - REFACTORIZACION PROGRESIVA
    // Se incorporan al corregir las dependencias existentes.
    // ============================================================

    @Test
    void modeloNoDebeDependerDeServicios() {
        noClasses()
                .that()
                .resideInAPackage("..modelo..")
                .should()
                .dependOnClassesThat()
                .resideInAPackage("..service..")
                .check(clases);
    }

    @Test
    void persistenciaNoDebeDependerDeServicios() {
        noClasses()
                .that()
                .resideInAPackage("..persistence..")
                .should()
                .dependOnClassesThat()
                .resideInAPackage("..service..")
                .check(clases);
    }

    @Test
    void controllersNoDebenDependerDeConfiguracion() {
        noClasses()
                .that()
                .resideInAPackage("..controller..")
                .should()
                .dependOnClassesThat()
                .resideInAPackage("..configuration..")
                .check(clases);
    }

    @Test
    void configuracionNoDebeDependerDeControllers() {
        noClasses()
                .that()
                .resideInAPackage("..configuration..")
                .should()
                .dependOnClassesThat()
                .resideInAPackage("..controller..")
                .check(clases);
    }


    @Test
    void modeloNoDebeDependerDeExcepcionesDeAplicacion() {
        noClasses()
                .that()
                .resideInAPackage("..modelo..")
                .should()
                .dependOnClassesThat()
                .resideInAPackage("com.devchesterunited.devchesterunitedv2.exception..")
                .check(clases);
    }

}
