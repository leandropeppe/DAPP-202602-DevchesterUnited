package com.devchesterunited.devchesterunitedv2.scraping.catalogo;

import com.devchesterunited.devchesterunitedv2.modelo.jugador.ProveedorIdentidadExterna;
import com.devchesterunited.devchesterunitedv2.persistence.equipo.entity.IdentidadExternaEquipoEntity;
import com.devchesterunited.devchesterunitedv2.persistence.equipo.repository.IdentidadExternaEquipoRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;

@Service
public class WhoscoredTeamIdentityPublicationService {
    private static final int EXPECTED_TOTAL = 96;
    private final IdentidadExternaEquipoRepository identities;

    public WhoscoredTeamIdentityPublicationService(IdentidadExternaEquipoRepository identities) {
        this.identities = identities;
    }

    @Transactional(rollbackFor = Exception.class)
    public WhoscoredTeamIdentityImporter.ImportResult publish(
            List<WhoscoredTeamIdentityCsv.Mapping> mappings) {
        validateCsv(mappings);
        List<PendingIdentity> pending = preflight(mappings);
        for (PendingIdentity item : pending) {
            identities.save(new IdentidadExternaEquipoEntity(UUID.randomUUID(), item.team(),
                    ProveedorIdentidadExterna.WHOSCORED, item.whoscoredId()));
        }
        return new WhoscoredTeamIdentityImporter.ImportResult(mappings.size(), pending.size(),
                mappings.size() - pending.size());
    }

    private void validateCsv(List<WhoscoredTeamIdentityCsv.Mapping> mappings) {
        if (mappings.size() != EXPECTED_TOTAL) {
            throw abort("Se esperaban exactamente 96 mappings: " + mappings.size());
        }
        Set<Long> whoscoredIds = new HashSet<>();
        Set<Long> footballDataIds = new HashSet<>();
        for (var mapping : mappings) {
            if (mapping.whoscoredTeamId() <= 0 || mapping.footballDataExternalId() <= 0) {
                throw abort("Los IDs deben ser mayores que cero");
            }
            if (!whoscoredIds.add(mapping.whoscoredTeamId())) {
                throw abort("WhoScored duplicado: " + mapping.whoscoredTeamId());
            }
            if (!footballDataIds.add(mapping.footballDataExternalId())) {
                throw abort("Football-Data duplicado: " + mapping.footballDataExternalId());
            }
        }
    }

    private List<PendingIdentity> preflight(List<WhoscoredTeamIdentityCsv.Mapping> mappings) {
        List<PendingIdentity> pending = new java.util.ArrayList<>();
        for (var mapping : mappings) {
            var footballData = identities.findByProveedorAndExternalId(
                    ProveedorIdentidadExterna.FOOTBALL_DATA, mapping.footballDataExternalId())
                    .orElseThrow(() -> abort("No existe Football-Data: " + mapping.footballDataExternalId()));
            if (!footballData.getEquipo().isActivo()) {
                throw abort("El equipo está inactivo: " + footballData.getEquipo().getId());
            }

            var whoscored = identities.findByProveedorAndExternalId(
                    ProveedorIdentidadExterna.WHOSCORED, mapping.whoscoredTeamId());
            if (whoscored.isPresent()) {
                if (!whoscored.get().getEquipo().getId().equals(footballData.getEquipo().getId())) {
                    throw abort("WhoScored ya apunta a otro equipo: " + mapping.whoscoredTeamId());
                }
                continue;
            }
            var teamIdentity = identities.findByEquipo_IdAndProveedor(
                    footballData.getEquipo().getId(), ProveedorIdentidadExterna.WHOSCORED);
            if (teamIdentity.isPresent() && !teamIdentity.get().getExternalId().equals(mapping.whoscoredTeamId())) {
                throw abort("El equipo ya posee otro WhoScored: " + footballData.getEquipo().getId());
            }
            pending.add(new PendingIdentity(footballData.getEquipo(), mapping.whoscoredTeamId()));
        }
        return pending;
    }

    private static IllegalArgumentException abort(String message) {
        return new IllegalArgumentException("Importación abortada: " + message);
    }

    private record PendingIdentity(com.devchesterunited.devchesterunitedv2.persistence.equipo.entity.EquipoEntity team,
                                   long whoscoredId) { }
}
