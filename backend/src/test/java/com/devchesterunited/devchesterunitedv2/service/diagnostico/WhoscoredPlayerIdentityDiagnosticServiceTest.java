package com.devchesterunited.devchesterunitedv2.service.diagnostico;

import static org.assertj.core.api.Assertions.assertThat;

import com.devchesterunited.devchesterunitedv2.modelo.IdentidadEstadisticaJugador;
import com.devchesterunited.devchesterunitedv2.modelo.ProveedorIdentidadExterna;
import com.devchesterunited.devchesterunitedv2.persistence.dto.JugadorEntity;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class WhoscoredPlayerIdentityDiagnosticServiceTest {
    @Test
    void diagnosticNormalizationRemovesDiacriticsAndNormalizesPunctuation() {
        assertThat(WhoscoredPlayerIdentityDiagnosticService.diagnosticNormalize(" Ronald Araújo "))
                .isEqualTo("ronald araujo");
        assertThat(WhoscoredPlayerIdentityDiagnosticService.diagnosticNormalize("Van-de Ven"))
                .isEqualTo("van de ven");
    }

    @Test
    void normalizedLevenshteinRanksTheClosestCandidates() {
        var external = new IdentidadEstadisticaJugador(1L, "Micky van de Ven", "Defender",
                "Netherlands", 10L, "Tottenham");
        var close = player("Mickey van de Ven");
        var far = player("Mohammed Kudus");
        var ranked = WhoscoredPlayerIdentityDiagnosticService.rankCandidates(external, List.of(far, close),
                java.util.Map.of(close.getId(), 131177L, far.getId(), 77399L));
        assertThat(ranked).extracting(candidate -> candidate.player().getNombre())
                .containsExactly("Mickey van de Ven", "Mohammed Kudus");
        assertThat(ranked.get(0).similarity()).isGreaterThan(ranked.get(1).similarity());
    }

    @Test
    void currentRuleStillRequiresExactNameAndCompatibleOptionalAttributes() {
        var external = new IdentidadEstadisticaJugador(1L, "Amar Dedic", "Defender", "Austria",
                10L, "Newcastle");
        assertThat(WhoscoredPlayerIdentityDiagnosticService.currentRuleMatches(player("Amar Dedić"), external))
                .isFalse();
    }

    private static JugadorEntity player(String name) {
        return new JugadorEntity(UUID.randomUUID(), name, UUID.randomUUID(), "Defender", null,
                "Netherlands", true);
    }
}
