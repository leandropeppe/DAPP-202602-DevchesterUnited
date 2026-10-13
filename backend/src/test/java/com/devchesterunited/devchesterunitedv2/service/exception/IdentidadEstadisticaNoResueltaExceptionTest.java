package com.devchesterunited.devchesterunitedv2.service.exception;

import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class IdentidadEstadisticaNoResueltaExceptionTest {
    @Test
    void messageResumeCantidadesMotivosYMuestrasAcotadas() {
        var diagnosticos = new ArrayList<IdentidadEstadisticaNoResueltaException.Diagnostico>();
        for (int i = 0; i < 11; i++) diagnosticos.add(diagnostico(
                IdentidadEstadisticaNoResueltaException.Tipo.EQUIPO, i,
                "Equipo " + i, IdentidadEstadisticaNoResueltaException.Motivo.SIN_CANDIDATOS));
        for (int i = 0; i < 20; i++) diagnosticos.add(diagnostico(
                IdentidadEstadisticaNoResueltaException.Tipo.JUGADOR, i + 100,
                "Jugador " + i, IdentidadEstadisticaNoResueltaException.Motivo.EQUIPO_NO_RESUELTO));
        diagnosticos.add(diagnostico(IdentidadEstadisticaNoResueltaException.Tipo.JUGADOR, 999,
                "Jugador fuera de muestra", IdentidadEstadisticaNoResueltaException.Motivo.SIN_CANDIDATOS));

        String message = new IdentidadEstadisticaNoResueltaException(diagnosticos).getMessage();

        assertThat(message).contains("32 identidad(es)")
                .contains("EQUIPO=11, JUGADOR=21")
                .contains("SIN_CANDIDATOS=12")
                .contains("EQUIPO_NO_RESUELTO=20")
                .contains("externalId=0")
                .contains("nombre=Equipo 0")
                .contains("externalId=100")
                .contains("nombre=Jugador 0")
                .contains("mejorCandidato=Mejor (id=id-1, score=0.91)")
                .contains("(+1");
        assertThat(message).doesNotContain("Equipo 10").doesNotContain("Jugador fuera de muestra");
    }

    @Test
    void conservaDiagnosticoDeJugadorBloqueadoPorEquipoNoResuelto() {
        var diagnostico = new IdentidadEstadisticaNoResueltaException.Diagnostico(
                IdentidadEstadisticaNoResueltaException.Tipo.JUGADOR, "WHOSCORED", 44L,
                "Jugador", "PREMIER_LEAGUE", "Equipo inexistente", null, null, null,
                IdentidadEstadisticaNoResueltaException.Motivo.EQUIPO_NO_RESUELTO);

        assertThat(new IdentidadEstadisticaNoResueltaException(List.of(diagnostico)).getMessage())
                .contains("EQUIPO_NO_RESUELTO=1")
                .contains("equipoWhoScored=Equipo inexistente")
                .contains("motivo=EQUIPO_NO_RESUELTO");
    }

    private static IdentidadEstadisticaNoResueltaException.Diagnostico diagnostico(
            IdentidadEstadisticaNoResueltaException.Tipo tipo, long externalId, String nombre,
            IdentidadEstadisticaNoResueltaException.Motivo motivo) {
        return new IdentidadEstadisticaNoResueltaException.Diagnostico(tipo, "WHOSCORED", externalId,
                nombre, "PREMIER_LEAGUE", tipo == IdentidadEstadisticaNoResueltaException.Tipo.JUGADOR
                        ? "Equipo" : nombre, null,
                new IdentidadEstadisticaNoResueltaException.Candidato("id-1", "Mejor", 0.91),
                new IdentidadEstadisticaNoResueltaException.Candidato("id-2", "Segundo", 0.71), motivo);
    }
}
