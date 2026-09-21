package com.devchesterunited.devchesterunitedv2.modelo;

import com.devchesterunited.devchesterunitedv2.modelo.exception.JugadorException;
import java.util.UUID;
import java.util.stream.IntStream;
import java.util.stream.Stream;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.junit.jupiter.params.provider.ValueSource;
import static org.assertj.core.api.Assertions.*;

/** T039: contrato Java propuesto; constructor completo y getters en español, sin infraestructura. */
class JugadorTest {
    private static final UUID ID = UUID.fromString("00000000-0000-0000-0000-000000000001");

    @Test
    void construccionValidaConservaTodosLosCampos() {
        var jugador = crear(ID, campos(), 9);
        assertThat(jugador.getId()).isEqualTo(ID);
        assertThat(jugador.getNombre()).isEqualTo(campos()[0]);
        /*
        assertThat(jugador.getNombre()).isEqualTo("Julián");
        assertThat(jugador.getNombre()).isEqualTo("Julián Álvarez");
        */
        assertThat(jugador.getLiga()).isEqualTo("LaLiga");
        assertThat(jugador.getEquipo()).isEqualTo("Atlético de Madrid");
        assertThat(jugador.getPosicion()).isEqualTo("Forward");
        assertThat(jugador.getDorsal()).isEqualTo(9);
        assertThat(jugador.getNacionalidad()).isEqualTo("Argentina");
    }

    @ParameterizedTest(name = "campo {0}, entrada inválida {1}")
    @MethodSource("camposVacios")
    void rechazaCadaCampoObligatorioVacio(int indice, String valor) {
        String[] datos = campos();
        datos[indice] = valor;
        assertThatThrownBy(() -> crear(ID, datos, 9)).isInstanceOf(JugadorException.class);
    }

    static Stream<Arguments> camposVacios() {
        return IntStream.range(0, 5).boxed().flatMap(i ->
                Stream.of(null, "", " ", "\t\n").map(valor -> Arguments.of(i, valor)));
    }

    @Test
    void rechazaIdentidadNulaConExcepcionPropia() {
        var datos = campos();
        assertThatThrownBy(() -> crear(null, datos, 9)).isInstanceOf(JugadorException.class);
    }

    @ParameterizedTest
    @ValueSource(strings = {"Forward", "Midfielder", "Defender", "Goalkeeper"})
    void admiteExclusivamenteLasPosicionesCanonicas(String posicion) {
        String[] datos = campos();
        datos[3] = posicion;
        assertThat(crear(ID, datos, 9).getPosicion()).isEqualTo(posicion);
    }

    @ParameterizedTest
    @ValueSource(strings = {"Striker", "forward", "FORWARD", "Delantero", " Forward ", "Forward/Midfielder"})
    void rechazaPosicionFueraDelDominio(String posicion) {
        String[] datos = campos();
        datos[3] = posicion;
        assertThatThrownBy(() -> crear(ID, datos, 9)).isInstanceOf(JugadorException.class);
    }

    @ParameterizedTest
    @ValueSource(ints = {1, 99})
    void aceptaLosLimitesDelDorsal(int dorsal) {
        assertThat(crear(ID, campos(), dorsal).getDorsal()).isEqualTo(dorsal);
    }

    @ParameterizedTest
    @ValueSource(ints = {0, 100, -1, Integer.MIN_VALUE, Integer.MAX_VALUE})
    void rechazaDorsalFueraDeRangoConExcepcionPropia(int dorsal) {
        var datos = campos();
        assertThatThrownBy(() -> crear(ID, datos, dorsal)).isInstanceOf(JugadorException.class);
    }

    @ParameterizedTest
    @ValueSource(ints = {0, 1, 2, 4})
    void aceptaLongitudMaximaDeDoscientosCaracteres(int indice) {
        String[] datos = campos();
        datos[indice] = "á".repeat(100);
        assertThatCode(() -> crear(ID, datos, 9)).doesNotThrowAnyException();
    }

    @ParameterizedTest
    @ValueSource(ints = {0, 1, 2, 4})
    void rechazaLongitudMayorADoscientosCaracteres(int indice) {
        String[] datos = campos();
        datos[indice] = "a".repeat(indice == 0 ? 201 : 101);
        assertThatThrownBy(() -> crear(ID, datos, 9)).isInstanceOf(JugadorException.class);
    }

    @Test
    void nombresYDorsalesCompartidosEntreEquiposNoImplicanUnicidad() {
        var primero = crear(ID, campos(), 9);
        String[] otros = campos();
        otros[2] = "Real Madrid";
        var segundo = crear(UUID.fromString("00000000-0000-0000-0000-000000000002"), otros, 9);
        assertThat(segundo.getNombre()).isEqualTo(primero.getNombre());
        assertThat(segundo.getDorsal()).isEqualTo(primero.getDorsal());
        assertThat(segundo.getEquipo()).isNotEqualTo(primero.getEquipo());
    }

    private static String[] campos() {
        return new String[]{"Julián Álvarez", "LaLiga", "Atlético de Madrid", "Forward", "Argentina"};
    }

    private static Jugador crear(UUID id, String[] datos, int dorsal) {
        return new Jugador(id, datos[0], datos[1], datos[2], datos[3], dorsal, datos[4]);
    }
}
