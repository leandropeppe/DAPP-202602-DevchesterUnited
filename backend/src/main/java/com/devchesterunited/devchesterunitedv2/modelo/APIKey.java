package com.devchesterunited.devchesterunitedv2.modelo;

import com.devchesterunited.devchesterunitedv2.modelo.exception.ApiKeyException;
import java.time.Instant;
import java.util.UUID;

public final class APIKey {
    private final UUID id;
    private final UUID usuarioId;
    private final String hashClave;
    private final String prefijo;
    private boolean activa;
    private final Instant creadaEn;
    private Instant revocadaEn;

    public APIKey(UUID id, UUID usuarioId, String hashClave, String prefijo, boolean activa,
            Instant creadaEn, Instant revocadaEn) {
        exigir(id, "El identificador de la API Key es obligatorio.");
        exigir(usuarioId, "El usuario de la API Key es obligatorio.");
        if (hashClave == null || !hashClave.matches("[0-9a-f]{64}")) {
            throw new ApiKeyException("El hash de la API Key no es válido.");
        }
        if (prefijo == null || prefijo.length() != 14) {
            throw new ApiKeyException("El prefijo de la API Key debe tener 14 caracteres.");
        }
        if (creadaEn == null) throw new ApiKeyException("La fecha de creación es obligatoria.");
        if (activa && revocadaEn != null) {
            throw new ApiKeyException("Una API Key activa no puede estar revocada.");
        }
        if (!activa && (revocadaEn == null || revocadaEn.isBefore(creadaEn))) {
            throw new ApiKeyException("Una API Key revocada debe tener una fecha válida.");
        }
        this.id = id;
        this.usuarioId = usuarioId;
        this.hashClave = hashClave;
        this.prefijo = prefijo;
        this.activa = activa;
        this.creadaEn = creadaEn;
        this.revocadaEn = revocadaEn;
    }

    private static void exigir(UUID valor, String mensaje) {
        if (valor == null) throw new ApiKeyException(mensaje);
    }

    public UUID getId() { return id; }
    public UUID getUsuarioId() { return usuarioId; }
    public String getHashClave() { return hashClave; }
    public String getPrefijo() { return prefijo; }
    public boolean isActiva() { return activa; }
    public Instant getCreadaEn() { return creadaEn; }
    public Instant getRevocadaEn() { return revocadaEn; }

    public void revocar(Instant momento) {
        if (!activa) throw new ApiKeyException("La API Key ya fue revocada.");
        if (momento == null || momento.isBefore(creadaEn)) {
            throw new ApiKeyException("La fecha de revocación no es válida.");
        }
        activa = false;
        revocadaEn = momento;
    }

    public void activar() {
        throw new ApiKeyException("Una API Key revocada no puede reactivarse.");
    }

    public boolean estaVigenteEn(Instant momento) {
        return activa && momento != null;
    }

    @Override
    public String toString() {
        return "APIKey[id=" + id + ", usuarioId=" + usuarioId
                + ", activa=" + activa + ", creadaEn=" + creadaEn + ", revocadaEn=" + revocadaEn + "]";
    }
}
