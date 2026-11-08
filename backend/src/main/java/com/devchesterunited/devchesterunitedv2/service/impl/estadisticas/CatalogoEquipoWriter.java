package com.devchesterunited.devchesterunitedv2.service.impl.estadisticas;

import com.devchesterunited.devchesterunitedv2.modelo.equipo.Equipo;
import com.devchesterunited.devchesterunitedv2.modelo.equipo.IdentidadExternaEquipo;
import com.devchesterunited.devchesterunitedv2.modelo.jugador.CatalogoSincronizado;
import com.devchesterunited.devchesterunitedv2.modelo.jugador.ProveedorIdentidadExterna;
import com.devchesterunited.devchesterunitedv2.persistence.equipo.entity.EquipoEntity;
import com.devchesterunited.devchesterunitedv2.persistence.equipo.entity.IdentidadExternaEquipoEntity;
import com.devchesterunited.devchesterunitedv2.persistence.equipo.repository.EquipoRepository;
import com.devchesterunited.devchesterunitedv2.persistence.equipo.repository.IdentidadExternaEquipoRepository;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@Component
public class CatalogoEquipoWriter {
    private final EquipoRepository equipos;
    private final IdentidadExternaEquipoRepository identidadesEquipos;

    public CatalogoEquipoWriter(EquipoRepository equipos,
            IdentidadExternaEquipoRepository identidadesEquipos) {
        this.equipos = equipos;
        this.identidadesEquipos = identidadesEquipos;
    }

    public Resultado escribir(CatalogoSincronizado snapshot, Instant ahora) {
        Map<UUID, UUID> equiposPersistidos = new HashMap<>();
        Map<Long, IdentidadExternaEquipoEntity> identidadesExistentes = identidadesExistentes(snapshot);
        int creados = 0;

        for (Equipo equipo : snapshot.equipos()) {
            if (guardar(equipo, snapshot, identidadesExistentes, equiposPersistidos, ahora)) {
                creados++;
            }
        }

        return new Resultado(equiposPersistidos, creados);
    }

    public void flush() {
        equipos.flush();
    }

    public List<IdentidadExternaEquipoEntity> obtenerIdentidadesFootballData() {
        return identidadesEquipos.findAllByProveedor(ProveedorIdentidadExterna.FOOTBALL_DATA);
    }

    public void inactivar(EquipoEntity equipo, Instant ahora) {
        equipos.save(EquipoEntity.builder()
                .id(equipo.getId())
                .nombre(equipo.getNombre())
                .nombreCorto(equipo.getNombreCorto())
                .sigla(equipo.getSigla())
                .liga(equipo.getLiga())
                .escudoUrl(equipo.getEscudoUrl())
                .activo(false)
                .temporadaExternaId(equipo.getTemporadaExternaId())
                .inicioTemporada(equipo.getInicioTemporada())
                .finTemporada(equipo.getFinTemporada())
                .creadoEn(equipo.getCreadoEn())
                .actualizadoEn(ahora)
                .build());
    }

    private boolean guardar(Equipo equipo, CatalogoSincronizado snapshot,
            Map<Long, IdentidadExternaEquipoEntity> identidadesExistentes,
            Map<UUID, UUID> equiposPersistidos, Instant ahora) {
        long externalId = snapshot.getIdentidadExternaEquipo(equipo.id()).externalId();
        IdentidadExternaEquipoEntity identidadExistente = identidadesExistentes.get(externalId);
        EquipoEntity anterior = identidadExistente == null ? null : identidadExistente.getEquipo();
        UUID id = anterior == null ? equipo.id() : anterior.getId();

        EquipoEntity persistido = equipos.save(EquipoEntity.builder()
                .id(id)
                .nombre(equipo.nombre())
                .nombreCorto(anterior == null ? null : anterior.getNombreCorto())
                .sigla(anterior == null ? null : anterior.getSigla())
                .liga(equipo.liga())
                .escudoUrl(anterior == null ? null : anterior.getEscudoUrl())
                .activo(true)
                .temporadaExternaId(equipo.temporada().temporadaExternaId())
                .inicioTemporada(equipo.temporada().inicio())
                .finTemporada(equipo.temporada().fin())
                .creadoEn(anterior == null ? ahora : anterior.getCreadoEn())
                .actualizadoEn(ahora)
                .build());

        equiposPersistidos.put(equipo.id(), persistido.getId());
        if (identidadExistente == null) {
            identidadesEquipos.save(new IdentidadExternaEquipoEntity(
                    UUID.randomUUID(), persistido, ProveedorIdentidadExterna.FOOTBALL_DATA, externalId));
        }
        return anterior == null;
    }

    private Map<Long, IdentidadExternaEquipoEntity> identidadesExistentes(
            CatalogoSincronizado snapshot) {
        Map<Long, IdentidadExternaEquipoEntity> resultado = new HashMap<>();
        List<Long> externalIds = snapshot.identidadesExternasEquipos().stream()
                .map(IdentidadExternaEquipo::externalId).toList();
        for (IdentidadExternaEquipoEntity identidad : identidadesEquipos
                .findAllByProveedorAndExternalIdIn(ProveedorIdentidadExterna.FOOTBALL_DATA, externalIds)) {
            if (identidad.getEquipo() == null || identidad.getEquipo().getId() == null) {
                throw new IllegalStateException("La identidad externa de equipo no tiene equipo local valido.");
            }
            resultado.put(identidad.getExternalId(), identidad);
        }
        return resultado;
    }

    public record Resultado(Map<UUID, UUID> equiposPersistidos, int creados) {
        public Resultado {
            equiposPersistidos = Map.copyOf(equiposPersistidos);
        }
    }
}
