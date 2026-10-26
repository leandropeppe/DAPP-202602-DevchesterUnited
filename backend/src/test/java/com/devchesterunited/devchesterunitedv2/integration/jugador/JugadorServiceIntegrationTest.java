package com.devchesterunited.devchesterunitedv2.integration.jugador;

import static org.assertj.core.api.Assertions.*;

import com.devchesterunited.devchesterunitedv2.controller.jugador.dto.FiltroJugadoresDTO;
import com.devchesterunited.devchesterunitedv2.service.dto.jugador.JugadorResultado;
import com.devchesterunited.devchesterunitedv2.service.dto.jugador.JugadoresResultado;
import com.devchesterunited.devchesterunitedv2.service.dto.shared.PaginacionResultado;
import com.devchesterunited.devchesterunitedv2.service.interfaces.jugador.JugadorService;
import com.devchesterunited.devchesterunitedv2.support.PostgresTestSupport;
import jakarta.validation.ConstraintViolationException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.annotation.Isolation;
import org.springframework.transaction.annotation.Transactional;

import java.util.Arrays;
import java.util.UUID;
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.junit.jupiter.api.Assertions.assertAll;

@SpringBootTest
@Transactional(isolation = Isolation.REPEATABLE_READ)
class JugadorServiceIntegrationTest extends PostgresTestSupport {
    @Autowired JugadorService servicio;
    @Autowired JdbcTemplate jdbc;

    @BeforeEach
    void prepararCatalogo() {
        limpiarTodosLosJugadoresYDependencias(jdbc);
        jdbc.update("UPDATE equipos SET is_active = TRUE");
        insertar(6, "Manuel", "Neuer", "Bundesliga", "Bayern", "Goalkeeper", 1, "Alemania");
        insertar(3, "Phil", "Foden", "Premier League", "Manchester City", "Forward", 47, "Inglaterra");
        insertar(1, "Julián", "Álvarez", "LaLiga", "Atlético de Madrid", "Forward", 9, "Argentina");
        insertar(5, "Jan", "Oblak", "LaLiga", "Atlético de Madrid", "Goalkeeper", 13, "Eslovenia");
        insertar(2, "Jude", "Bellingham", "LaLiga", "Real Madrid", "Midfielder", 5, "Inglaterra");
        insertar(4, "Dani", "Carvajal", "LaLiga", "Real Madrid", "Defender", 2, "España");
    }

    @Test
    void sinFiltrosUsaDefaultsYOrdenUuidAscendente() {
        var filtros = FiltroJugadoresDTO.desdeParametros(null, null, null, null, null);
        assertThat(filtros.pagina()).isEqualTo(1);
        assertThat(filtros.limite()).isEqualTo(20);
        assertThat(filtros.offset()).isZero();
        var respuesta = servicio.consultar(filtros);
        verificarIds(respuesta, 1, 2, 3, 4, 5, 6);
        verificarPaginacion(respuesta, 1, 20, 6, 1);
    }

    @ParameterizedTest
    @MethodSource("filtrosParciales")
    void filtraParcialmenteSinDistinguirMayusculas(String liga, String equipo, String posicion, int[] esperados) {
        var respuesta = servicio.consultar(new FiltroJugadoresDTO(liga, equipo, posicion, 1, 20));
        verificarIds(respuesta, esperados);
        verificarPaginacion(respuesta, 1, 20, esperados.length, 1);
    }

    static Stream<Arguments> filtrosParciales() {
        return Stream.of(
                Arguments.of("lAlIg", null, null, new int[]{1, 2, 4, 5}),
                Arguments.of(null, "mAdRiD", null, new int[]{1, 2, 4, 5}),
                Arguments.of(null, null, "wArD", new int[]{1, 3}));
    }

    @Test
    void combinaLosTresFiltrosConAnd() {
        var respuesta = servicio.consultar(new FiltroJugadoresDTO("liga", "madrid", "fielder", 1, 20));
        verificarIds(respuesta, 2);
        verificarPaginacion(respuesta, 1, 20, 1, 1);
    }

    @Test
    void recortaLosTresFiltrosSinEliminarAcentos() {
        var filtros = new FiltroJugadoresDTO("  LaLiga  ", "  Atlético  ", "  ward  ", null, null);
        assertThat(filtros.liga()).isEqualTo("LaLiga");
        assertThat(filtros.equipo()).isEqualTo("Atlético");
        assertThat(filtros.posicion()).isEqualTo("ward");
        var respuesta = servicio.consultar(filtros);
        verificarIds(respuesta, 1);
        verificarPaginacion(respuesta, 1, 20, 1, 1);
    }

    @ParameterizedTest
    @MethodSource("paginas")
    void paginaConOffsetLimiteYTotalPrevio(int pagina, int limite, long offset, long totalPaginas, int[] esperados) {
        var filtros = new FiltroJugadoresDTO(null, null, null, pagina, limite);
        assertThat(filtros.offset()).isEqualTo(offset);
        var respuesta = servicio.consultar(filtros);
        verificarIds(respuesta, esperados);
        verificarPaginacion(respuesta, pagina, limite, 6, totalPaginas);
    }

    static Stream<Arguments> paginas() {
        return Stream.of(
                Arguments.of(2, 2, 2L, 3L, new int[]{3, 4}),
                Arguments.of(2, 4, 4L, 2L, new int[]{5, 6}));
    }

    @Test
    void totalFiltradoSeCuentaAntesDePaginar() {
        var respuesta = servicio.consultar(new FiltroJugadoresDTO("liga", "madrid", null, 2, 3));
        verificarIds(respuesta, 5);
        verificarPaginacion(respuesta, 2, 3, 4, 2);
    }

    @Test
    void paginaFueraDeRangoConservaPaginaYTotales() {
        var respuesta = servicio.consultar(new FiltroJugadoresDTO("liga", "madrid", null, 4, 2));
        verificarIds(respuesta);
        verificarPaginacion(respuesta, 4, 2, 4, 2);
    }

    @Test
    void ceroCoincidenciasDevuelveCeroRegistrosYCeroPaginas() {
        var respuesta = servicio.consultar(new FiltroJugadoresDTO("Premier", "Madrid", null, 1, 20));
        verificarIds(respuesta);
        verificarPaginacion(respuesta, 1, 20, 0, 0);
    }

    @Test
    void mapeaTodosLosCamposPublicosYElIdentificadorOpaco() {
        var respuesta = servicio.consultar(new FiltroJugadoresDTO(null, null, null, 1, 20));
        assertThat(respuesta.jugadores()).containsExactly(
                new JugadorResultado(id(1), "Julián Álvarez", "LaLiga", "Atlético de Madrid", "Forward", 9, "Argentina"),
                new JugadorResultado(id(2), "Jude Bellingham", "LaLiga", "Real Madrid", "Midfielder", 5, "Inglaterra"),
                new JugadorResultado(id(3), "Phil Foden", "Premier League", "Manchester City", "Forward", 47, "Inglaterra"),
                new JugadorResultado(id(4), "Dani Carvajal", "LaLiga", "Real Madrid", "Defender", 2, "España"),
                new JugadorResultado(id(5), "Jan Oblak", "LaLiga", "Atlético de Madrid", "Goalkeeper", 13, "Eslovenia"),
                new JugadorResultado(id(6), "Manuel Neuer", "Bundesliga", "Bayern", "Goalkeeper", 1, "Alemania"));
        respuesta.jugadores().forEach(jugador -> assertThat(jugador.id()).isNotNull());
    }

    @ParameterizedTest(name = "filtro {0}, caso inválido {index}")
    @MethodSource("filtrosInvalidos")
    void rechazaFiltrosInvalidos(int campo, String valor) {
        assertThatThrownBy(() -> filtroIndividual(campo, valor)).isInstanceOf(ConstraintViolationException.class);
    }

    static Stream<Arguments> filtrosInvalidos() {
        return Stream.of(
                Arguments.of(0, "\tMadrid"),
                Arguments.of(1, ""),
                Arguments.of(2, "a".repeat(31)));
    }

    @ParameterizedTest
    @ValueSource(ints = {0, 2})
    void admiteLongitudMaximaDespuesDelTrimming(int campo) {
        String valor = "á".repeat(campo == 2 ? 30 : 100);
        var filtro = filtroIndividual(campo, "  " + valor + "  ");
        String conservado = switch (campo) { case 0 -> filtro.liga(); case 1 -> filtro.equipo(); default -> filtro.posicion(); };
        assertThat(conservado).isEqualTo(valor);
    }

    @Test
    void rechazaPaginacionFueraDeRango() {
        assertAll(
                () -> assertThatThrownBy(() -> new FiltroJugadoresDTO(null, null, null, 0, 20))
                        .isInstanceOf(ConstraintViolationException.class),
                () -> assertThatThrownBy(() -> new FiltroJugadoresDTO(null, null, null, -1, 20))
                        .isInstanceOf(ConstraintViolationException.class),
                () -> assertThatThrownBy(() -> new FiltroJugadoresDTO(null, null, null, 1, 0))
                        .isInstanceOf(ConstraintViolationException.class),
                () -> assertThatThrownBy(() -> new FiltroJugadoresDTO(null, null, null, 1, 101))
                        .isInstanceOf(ConstraintViolationException.class));
    }

    @ParameterizedTest(name = "parámetro {0}, entrada inválida {index}")
    @MethodSource("enterosInvalidos")
    void rechazaEnterosInvalidosDesdeParametros(String campo, String valor) {
        String pagina = campo.equals("page") ? valor : "1";
        String limite = campo.equals("limit") ? valor : "20";

        assertThatThrownBy(() ->
                FiltroJugadoresDTO.desdeParametros(
                        null,
                        null,
                        null,
                        pagina,
                        limite
                ))
                .isInstanceOf(ConstraintViolationException.class);
    }

    static Stream<Arguments> enterosInvalidos() {
        return Stream.of(
                Arguments.of("page", ""),
                Arguments.of("page", "abc"),
                Arguments.of("limit", "1.5"),
                Arguments.of("limit", "2147483648"));
    }

    @Test
    void rechazaOverflowDelOffsetAunquePageYLimitSeanValidos() {
        assertThatThrownBy(() -> new FiltroJugadoresDTO(null, null, null, Integer.MAX_VALUE, 100)).isInstanceOf(ConstraintViolationException.class);
    }

    @Test
    void aceptaLimitesValidosDePageLimitYOffset() {
        var maxPagina = new FiltroJugadoresDTO(null, null, null, Integer.MAX_VALUE, 1);
        assertThat(maxPagina.offset()).isEqualTo(2147483646L);
        var maxLimite = new FiltroJugadoresDTO(null, null, null, 1, 100);
        assertThat(maxLimite.offset()).isZero();
        assertThat(maxLimite.limite()).isEqualTo(100);
    }

    private static FiltroJugadoresDTO filtroIndividual(int campo, String valor) {
        return new FiltroJugadoresDTO(campo == 0 ? valor : null, campo == 1 ? valor : null, campo == 2 ? valor : null, 1, 20);
    }

    private static void verificarIds(JugadoresResultado respuesta, int... numeros) {
        assertThat(respuesta.jugadores()).extracting(JugadorResultado::id).containsExactlyElementsOf(Arrays.stream(numeros).mapToObj(JugadorServiceIntegrationTest::id).toList());
    }

    private static void verificarPaginacion(JugadoresResultado respuesta, int pagina, int limite, long registros, long paginas) {
        assertThat(respuesta.paginacion()).isEqualTo(new PaginacionResultado(pagina, limite, paginas, registros));
    }

    private void insertar(int numero, String nombre, String apellido, String liga, String equipo, String posicion, int dorsal, String nacionalidad) {
        jdbc.update("""
                INSERT INTO equipos (id, name, league, is_active, created_at, updated_at)
                VALUES ((md5(? || '::' || ?))::uuid, ?, ?, TRUE, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP)
                ON CONFLICT (id) DO UPDATE SET name = EXCLUDED.name, league = EXCLUDED.league, is_active = TRUE
                """, liga, equipo, equipo, liga);
        jdbc.update("INSERT INTO jugadores (id, name, team_id, position, jersey_number, nationality) VALUES (?, ?, (md5(? || '::' || ?))::uuid, ?, ?, ?)",
                id(numero), nombre + " " + apellido, liga, equipo, posicion, dorsal, nacionalidad);
    }

    private static UUID id(int numero) {
        return UUID.fromString("10000000-0000-0000-0000-" + String.format("%012d", numero));
    }

}
