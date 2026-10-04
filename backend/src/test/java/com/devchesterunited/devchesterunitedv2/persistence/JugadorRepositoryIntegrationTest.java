package com.devchesterunited.devchesterunitedv2.persistence;

import com.devchesterunited.devchesterunitedv2.configuration.PostgresTestSupport;
import com.devchesterunited.devchesterunitedv2.persistence.dto.JugadorCatalogoProjection;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.stream.Stream;
import org.hibernate.resource.jdbc.spi.StatementInspector;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.orm.jpa.HibernatePropertiesCustomizer;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.annotation.Transactional;
import static org.assertj.core.api.Assertions.*;

/** PostgreSQL real; fixtures independientes del Repository y restaurados por rollback. */
@SpringBootTest
@Import(JugadorRepositoryIntegrationTest.InspeccionSql.class)
@Transactional
class JugadorRepositoryIntegrationTest extends PostgresTestSupport {
    @Autowired JugadorRepository repositorio;
    @Autowired JdbcTemplate jdbc;
    @Autowired CapturaSql captura;

    @BeforeEach
    void prepararCatalogo() {
        jdbc.update("DELETE FROM jugadores");
        jdbc.update("UPDATE equipos SET is_active = TRUE");
        // Orden de inserción deliberadamente distinto al UUID.
        insertar(6, "Liga prueba", "Club sin símbolos", "Defender");
        insertar(3, "Premier League", "Manchester City", "Forward");
        insertar(1, "LaLiga", "Real Madrid", "Forward");
        insertar(5, "Liga prueba", "Club !%_ O'Brien \"Azul\"", "Goalkeeper");
        insertar(4, "LaLiga", "Atletico de Madrid", "Defender");
        insertar(2, "LaLiga", "Atlético de Madrid", "Midfielder");
        captura.limpiar();
    }

    @Test
    void sinFiltrosDevuelveTodoOrdenadoPorUuid() {
        verificar(null, null, null, 1, 2, 3, 4, 5, 6);
    }

    @ParameterizedTest
    @MethodSource("filtrosParciales")
    void filtrosParcialesIgnoranMayusculas(String liga, String equipo, String posicion, List<UUID> esperados) {
        assertThat(ids(repositorio.buscar(liga, equipo, posicion, 0, 100))).containsExactlyElementsOf(esperados);
        assertThat(repositorio.contar(liga, equipo, posicion)).isEqualTo(esperados.size());
    }

    static Stream<Arguments> filtrosParciales() {
        return Stream.of(
                Arguments.of("lIgA", null, null, List.of(id(1), id(2), id(4), id(5), id(6))),
                Arguments.of(null, "mAdRiD", null, List.of(id(1), id(2), id(4))),
                Arguments.of(null, null, "wArD", List.of(id(1), id(3))));
    }

    @ParameterizedTest
    @ValueSource(strings = {"%", "_", "!", "!%_", "O'Brien", "\"Azul\""})
    void simbolosYComillasSonLiteralesEnEquipo(String filtro) {
        verificar(null, filtro, null, 5);
    }

    @ParameterizedTest
    @ValueSource(strings = {"%", "_", "!", "!%_", "O'Brien", "\"Azul\""})
    void simbolosYComillasSonLiteralesEnLiga(String filtro) {
        jdbc.update("UPDATE equipos e SET league = ? FROM jugadores j WHERE e.id = j.team_id AND j.id = ?",
                "Copa !%_ O'Brien \"Azul\"", id(5));
        verificar(filtro, null, null, 5);
    }

    @ParameterizedTest
    @ValueSource(strings = {"%", "_", "!", "'"})
    void posicionNoInterpretaComodinesNiComillas(String filtro) {
        verificar(null, null, filtro);
    }

    @Test
    void acentosSiguenSiendoSignificativos() {
        verificar(null, "ATLÉTICO", null, 2);
        verificar(null, "ATLETICO", null, 4);
    }

    @Test
    void noConservaCopiaLegacyYResuelveDatosDesdeEquipo() {
        assertThat(jdbc.queryForObject("SELECT count(*) FROM information_schema.columns WHERE table_schema = current_schema() AND table_name = 'jugadores' AND column_name IN ('league', 'team')", Integer.class)).isZero();
        jdbc.update("UPDATE equipos e SET league = ?, name = ? FROM jugadores j WHERE e.id = j.team_id AND j.id = ?",
                "Liga actualizada", "Equipo actualizado", id(1));

        List<JugadorCatalogoProjection> resultado = repositorio.buscar("actualizada", "actualizado", null, 0, 100);

        assertThat(ids(resultado)).containsExactly(id(1));
        assertThat(resultado.get(0).getLiga()).isEqualTo("Liga actualizada");
        assertThat(resultado.get(0).getEquipo()).isEqualTo("Equipo actualizado");
    }

    @Test
    void combinaLosTresFiltrosConAnd() {
        verificar("liga", "madrid", "fielder", 2);
        verificar("premier", "madrid", "forward");
    }

    @Test
    void contarNoDependeDeOffsetNiLimite() {
        assertThat(repositorio.contar("liga", "madrid", null)).isEqualTo(3);
        assertThat(ids(repositorio.buscar("liga", "madrid", null, 1, 1))).containsExactly(id(2));
        assertThat(repositorio.contar("liga", "madrid", null)).isEqualTo(3);
    }

    @Test
    void paginasEstablesSinDuplicadosNiSaltos() {
        List<UUID> primera = ids(repositorio.buscar(null, null, null, 0, 2));
        List<UUID> segunda = ids(repositorio.buscar(null, null, null, 2, 2));
        List<UUID> tercera = ids(repositorio.buscar(null, null, null, 4, 2));
        assertThat(primera).containsExactly(id(1), id(2));
        assertThat(segunda).containsExactly(id(3), id(4));
        assertThat(tercera).containsExactly(id(5), id(6));
        assertThat(ids(repositorio.buscar(null, null, null, 2, 2))).containsExactlyElementsOf(segunda);
        assertThat(repositorio.contar(null, null, null)).isEqualTo(6);
    }

    @Test
    void limiteMayorQueRestantesDevuelveSoloRestantes() {
        assertThat(ids(repositorio.buscar(null, null, null, 5, 20))).containsExactly(id(6));
        assertThat(repositorio.contar(null, null, null)).isEqualTo(6);
    }

    @Test
    void offsetIgualOSuperiorAlTotalDevuelvePaginaVacia() {
        assertThat(repositorio.buscar(null, null, null, 6, 2)).isEmpty();
        assertThat(repositorio.buscar(null, null, null, 100, 2)).isEmpty();
        assertThat(repositorio.contar(null, null, null)).isEqualTo(6);
    }

    @Test
    void sinCoincidenciasConteoCeroYListaVacia() {
        verificar("Liga inexistente", null, null);
    }

    @Test
    void sqlRecibeParametrosEnlazadosEnBusquedaConteoYPaginacion() {
        String entrada = "x' OR '1'='1";
        jdbc.update("UPDATE equipos e SET name = ? FROM jugadores j WHERE e.id = j.team_id AND j.id = ?",
                entrada, id(5));
        captura.limpiar();
        // El texto sospechoso debe encontrar solo su fila literal, sin ampliar la búsqueda.
        assertThat(ids(repositorio.buscar("prueba", entrada, "keeper", 0, 7))).containsExactly(id(5));
        assertThat(repositorio.contar("prueba", entrada, "keeper")).isEqualTo(1);
        List<String> consultas = captura.consultas();
        assertThat(consultas).hasSize(2);
        for (String sql : consultas) {
            assertThat(sql).contains("?").doesNotContain(entrada, "prueba", "keeper");
        }
        assertThat(consultas.get(0)).containsPattern("(?is).*limit\\s+\\?\\s+offset\\s+\\?.*");
        assertThat(consultas.get(1)).containsIgnoringCase("count(*)")
                .doesNotContainIgnoringCase("limit", "offset");
        assertThat(jdbc.queryForObject("SELECT count(*) FROM jugadores", Long.class)).isEqualTo(6);
    }

    private void verificar(String liga, String equipo, String posicion, int... numeros) {
        List<UUID> esperados = java.util.Arrays.stream(numeros).mapToObj(JugadorRepositoryIntegrationTest::id).toList();
        assertThat(ids(repositorio.buscar(liga, equipo, posicion, 0, 100))).containsExactlyElementsOf(esperados);
        assertThat(repositorio.contar(liga, equipo, posicion)).isEqualTo(numeros.length);
    }

    private void insertar(int numero, String liga, String equipo, String posicion) {
        jdbc.update("""
                INSERT INTO equipos (id, name, league, is_active, created_at, updated_at)
                VALUES ((md5(? || '::' || ?))::uuid, ?, ?, TRUE, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP)
                ON CONFLICT (id) DO UPDATE SET name = EXCLUDED.name, league = EXCLUDED.league, is_active = TRUE
                """, liga, equipo, equipo, liga);
        jdbc.update("INSERT INTO jugadores (id, name, team_id, position, jersey_number, nationality) VALUES (?, ?, (md5(? || '::' || ?))::uuid, ?, ?, ?)",
                id(numero), "Nombre Apellido", liga, equipo, posicion, numero, "Argentina");
    }

    private static UUID id(int numero) {
        return UUID.fromString("10000000-0000-0000-0000-" + String.format("%012d", numero));
    }

    private static List<UUID> ids(List<JugadorCatalogoProjection> jugadores) {
        return jugadores.stream().map(JugadorCatalogoProjection::getId).toList();
    }

    /** Observa SQL real; no sustituye conexiones, consultas ni resultados. */
    static class CapturaSql implements StatementInspector {
        private final ThreadLocal<List<String>> sql = ThreadLocal.withInitial(ArrayList::new);
        @Override public String inspect(String consulta) {
            sql.get().add(consulta);
            return consulta;
        }
        void limpiar() { sql.remove(); }
        List<String> consultas() { return List.copyOf(sql.get()); }
    }

    @TestConfiguration(proxyBeanMethods = false)
    static class InspeccionSql {
        @Bean CapturaSql capturaSql() { return new CapturaSql(); }
        @Bean HibernatePropertiesCustomizer inspector(CapturaSql captura) {
            return propiedades -> propiedades.put("hibernate.session_factory.statement_inspector", captura);
        }
    }
}
