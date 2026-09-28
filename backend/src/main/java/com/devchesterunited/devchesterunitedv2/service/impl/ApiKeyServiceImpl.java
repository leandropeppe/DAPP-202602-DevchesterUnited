package com.devchesterunited.devchesterunitedv2.service.impl;

import com.devchesterunited.devchesterunitedv2.service.dto.ApiKeyResultado;
import com.devchesterunited.devchesterunitedv2.persistence.ApiKeyRepository;
import com.devchesterunited.devchesterunitedv2.persistence.UsuarioRepository;
import com.devchesterunited.devchesterunitedv2.service.interfaces.ApiKeyService;
import com.devchesterunited.devchesterunitedv2.service.interfaces.AutenticacionService;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.nio.charset.StandardCharsets;
import java.util.UUID;
import org.springframework.dao.DataAccessException;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.stereotype.Service;

@Service
public class ApiKeyServiceImpl implements ApiKeyService {
    private static final String CREDENCIAL_INVALIDA = "No autorizado: API Key inválida o ausente";
    private final AutenticacionService autenticacion;
    private final ApiKeyRepository apiKeys;
    private final UsuarioRepository usuarios;

    public ApiKeyServiceImpl(AutenticacionService autenticacion, ApiKeyRepository apiKeys, UsuarioRepository usuarios) {
        this.autenticacion = autenticacion;
        this.apiKeys = apiKeys;
        this.usuarios = usuarios;
    }

    @Override
    public ApiKeyResultado generar(UUID usuarioId) {
        return autenticacion.generarApiKey(usuarioId);
    }

    @Override
    public UUID validar(String apiKey) {
        if (apiKey == null || !apiKey.matches("dev_key_[A-Za-z0-9_-]{43}")) {
            throw new BadCredentialsException(CREDENCIAL_INVALIDA);
        }
        String hash = sha256(apiKey);
        var entidad = apiKeys.findByHashClaveAndActivaTrue(hash)
                .orElseThrow(() -> new BadCredentialsException(CREDENCIAL_INVALIDA));
        if (usuarios.findById(entidad.getUsuarioId()).isEmpty()) {
            throw new BadCredentialsException(CREDENCIAL_INVALIDA);
        }
        return entidad.getUsuarioId();
    }

    private static String sha256(String valor) {
        try {
            byte[] digest = MessageDigest.getInstance("SHA-256").digest(valor.getBytes(StandardCharsets.UTF_8));
            StringBuilder resultado = new StringBuilder(64);
            for (byte parte : digest) resultado.append(String.format("%02x", parte));
            return resultado.toString();
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException("No se pudo validar la API Key.", exception);
        }
    }
}
