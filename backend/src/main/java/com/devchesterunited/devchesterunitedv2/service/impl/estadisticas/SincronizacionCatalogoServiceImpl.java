package com.devchesterunited.devchesterunitedv2.service.impl.estadisticas;

import com.devchesterunited.devchesterunitedv2.exception.SincronizacionPersistenciaException;
import com.devchesterunited.devchesterunitedv2.modelo.jugador.CatalogoSincronizado;
import com.devchesterunited.devchesterunitedv2.scraping.catalogo.ProveedorCatalogo;
import com.devchesterunited.devchesterunitedv2.service.interfaces.estadisticas.SincronizacionCatalogoService;
import org.springframework.dao.DataAccessException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Isolation;
import org.springframework.transaction.annotation.Transactional;

import java.util.Objects;
import java.util.UUID;

@Service
public class SincronizacionCatalogoServiceImpl implements SincronizacionCatalogoService {
    private final ProveedorCatalogo adapter;
    private final CatalogoSyncCoordinator coordinator;
    private final CatalogoUsuarioValidator usuarioValidator;
    private final CatalogoSyncLock syncLock;

    @org.springframework.beans.factory.annotation.Autowired
    public SincronizacionCatalogoServiceImpl(ProveedorCatalogo adapter,
            CatalogoSyncCoordinator coordinator, CatalogoUsuarioValidator usuarioValidator,
            CatalogoSyncLock syncLock) {
        this.adapter = adapter;
        this.coordinator = coordinator;
        this.usuarioValidator = Objects.requireNonNull(usuarioValidator,
                "usuarioValidator es obligatorio");
        this.syncLock = Objects.requireNonNull(syncLock, "syncLock es obligatorio");
    }

    @Override
    @Transactional(timeout = 150, isolation = Isolation.READ_COMMITTED)
    public SincronizacionResultado sincronizar() {
        syncLock.adquirir();

        try {
            CatalogoSincronizado snapshot = adapter.sincronizar();
            return coordinator.procesar(snapshot);
        } catch (com.devchesterunited.devchesterunitedv2.scraping.catalogo.FootballDataException exception) {
            throw exception;
        } catch (DataAccessException exception) {
            throw new SincronizacionPersistenciaException(exception);
        }
    }

    @Override
    public void validarUsuario(UUID usuarioId) {
        usuarioValidator.validar(usuarioId);
    }
}
