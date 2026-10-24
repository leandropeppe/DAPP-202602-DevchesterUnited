package com.devchesterunited.devchesterunitedv2.service.impl;

import com.devchesterunited.devchesterunitedv2.configuration.JwtConfig;
import com.devchesterunited.devchesterunitedv2.modelo.Usuario;
import com.devchesterunited.devchesterunitedv2.persistence.UsuarioRepository;
import com.devchesterunited.devchesterunitedv2.persistence.ApiKeyRepository;
import com.devchesterunited.devchesterunitedv2.persistence.dto.APIKeyEntity;
import com.devchesterunited.devchesterunitedv2.persistence.dto.UsuarioEntity;
import com.devchesterunited.devchesterunitedv2.service.interfaces.AutenticacionService;
import com.devchesterunited.devchesterunitedv2.service.dto.ApiKeyResultado;
import com.devchesterunited.devchesterunitedv2.service.dto.LoginResultado;
import com.devchesterunited.devchesterunitedv2.service.dto.UsuarioResultado;
import java.time.Clock;
import java.time.Instant;
import java.util.Locale;
import java.util.UUID;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.util.Base64;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.dao.ConcurrencyFailureException;
import org.springframework.dao.DataAccessException;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import java.sql.SQLException;
import org.springframework.transaction.TransactionDefinition;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

@Service
public class AutenticacionServiceImpl implements AutenticacionService {
    private static final Logger LOGGER = LoggerFactory.getLogger(AutenticacionServiceImpl.class);
    private static final String MENSAJE_CREDENCIALES = "Nombre de usuario o contraseña incorrectos";
    private static final String MENSAJE_DUPLICADO = "El nombre de usuario o el correo electrónico ya está registrado.";
    private final UsuarioRepository repositorio;
    private final ApiKeyRepository apiKeyRepository;
    private final PasswordEncoder codificador;
    private final JwtConfig jwtConfig;
    private final Clock clock;
    private final AsignacionCreditosInicialesService asignacionesCreditos;
    private final SecureRandom secureRandom = new SecureRandom();
    private final TransactionTemplate transactionTemplate;

    public AutenticacionServiceImpl(UsuarioRepository repositorio, ApiKeyRepository apiKeyRepository,
            PasswordEncoder codificador, JwtConfig jwtConfig, Clock clock,
            org.springframework.transaction.PlatformTransactionManager transactionManager,
            AsignacionCreditosInicialesService asignacionesCreditos) {
        this.repositorio = repositorio;
        this.apiKeyRepository = apiKeyRepository;
        this.codificador = codificador;
        this.jwtConfig = jwtConfig;
        this.clock = clock;
        this.asignacionesCreditos = asignacionesCreditos;
        this.transactionTemplate = new TransactionTemplate(transactionManager);
        this.transactionTemplate.setIsolationLevel(TransactionDefinition.ISOLATION_READ_COMMITTED);
    }

    @Override
    @Transactional
    public UsuarioResultado registrar(String nombreUsuario, String correo, String contrasena) {
        String nombreNormalizado = normalizar(nombreUsuario);
        String correoNormalizado = normalizar(correo);
        if (repositorio.findByNombreUsuario(nombreNormalizado).isPresent()
                || repositorio.findByCorreo(correoNormalizado).isPresent()) {
            throw new DataIntegrityViolationException(MENSAJE_DUPLICADO);
        }
        Instant ahora = clock.instant();
        UUID id = UUID.randomUUID();
        String hash = codificador.encode(contrasena);
        Usuario usuario = new Usuario(id, nombreNormalizado, correoNormalizado, hash, ahora, ahora);
        UsuarioEntity entidad = aEntidad(usuario);
        try {
            UsuarioEntity guardado = repositorio.saveAndFlush(entidad);
            programarAsignacionTrasCommit(guardado.getId());
            return aRespuesta(aDominio(guardado));
        } catch (DataIntegrityViolationException conflicto) {
            throw new DataIntegrityViolationException(MENSAJE_DUPLICADO);
        }
    }

    private void programarAsignacionTrasCommit(UUID usuarioId) {
        TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
            @Override
            public void afterCommit() {
                try {
                    asignacionesCreditos.asignarParaUsuario(usuarioId);
                } catch (RuntimeException fallo) {
                    LOGGER.warn("No se pudo asignar el credito inicial al usuario {}; "
                            + "el procesamiento idempotente posterior lo recuperara.", usuarioId, fallo);
                }
            }
        });
    }

    @Override
    @Transactional(readOnly = true)
    public LoginResultado login(String nombreUsuario, String contrasena) {
        String nombreNormalizado = normalizar(nombreUsuario);
        var entidad = repositorio.findByNombreUsuario(nombreNormalizado).orElse(null);
        if (entidad == null || contrasena == null || !codificador.matches(contrasena, entidad.getHashContrasena())) {
            throw new BadCredentialsException(MENSAJE_CREDENCIALES);
        }
        String token = jwtConfig.jwtEncoder().encode(jwtConfig.parametrosPara(entidad.getId())).getTokenValue();
        return new LoginResultado(token);
    }

    @Override
    public ApiKeyResultado generarApiKey(UUID usuarioId) {
        if (usuarioId == null) throw new BadCredentialsException("No autorizado.");
        try {
            ApiKeyResultado resultado = transactionTemplate.execute(status -> generarDentroDeTransaccion(usuarioId));
            if (resultado == null) throw new IllegalStateException("No se pudo generar la API Key.");
            return resultado;
        } catch (DataAccessException exception) {
            if (esLockNoWait(exception)) {
                throw new ConcurrencyFailureException("Otra solicitud está generando una API Key para este usuario.");
            }
            throw exception;
        }
    }

    private ApiKeyResultado generarDentroDeTransaccion(UUID usuarioId) {
        if (repositorio.findByIdForUpdateNoWait(usuarioId).isEmpty()) {
            throw new BadCredentialsException("No autorizado.");
        }
        apiKeyRepository.findByUsuarioIdAndActivaTrue(usuarioId).ifPresent(anterior -> {
            var dominio = new com.devchesterunited.devchesterunitedv2.modelo.APIKey(anterior.getId(), anterior.getUsuarioId(),
                    anterior.getHashClave(), anterior.getPrefijo(), anterior.isActiva(), anterior.getCreadaEn(), anterior.getRevocadaEn());
            dominio.revocar(clock.instant());
            apiKeyRepository.saveAndFlush(anteriorRevocada(anterior, dominio.getRevocadaEn()));
        });
        byte[] secreto = new byte[32];
        secureRandom.nextBytes(secreto);
        String token = "dev_key_" + Base64.getUrlEncoder().withoutPadding().encodeToString(secreto);
        String hash = sha256(token);
        String prefijo = token.substring(0, 14);
        Instant creada = clock.instant();
        apiKeyRepository.saveAndFlush(new APIKeyEntity(UUID.randomUUID(), usuarioId, hash, prefijo, true, creada, null));
        return new ApiKeyResultado(token, prefijo, true, creada);
    }

    private static APIKeyEntity anteriorRevocada(APIKeyEntity anterior, Instant revocada) {
        return new APIKeyEntity(anterior.getId(), anterior.getUsuarioId(), anterior.getHashClave(), anterior.getPrefijo(), false,
                anterior.getCreadaEn(), revocada);
    }

    private static String sha256(String valor) {
        try {
            byte[] digest = MessageDigest.getInstance("SHA-256").digest(valor.getBytes(java.nio.charset.StandardCharsets.UTF_8));
            StringBuilder resultado = new StringBuilder(64);
            for (byte parte : digest) resultado.append(String.format("%02x", parte));
            return resultado.toString();
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException("No se pudo calcular el hash de la API Key.", exception);
        }
    }

    private static boolean esLockNoWait(Throwable error) {
        for (Throwable actual = error; actual != null; actual = actual.getCause()) {
            if (actual instanceof SQLException sql && "55P03".equals(sql.getSQLState())) return true;
        }
        return false;
    }

    private static String normalizar(String valor) {
        return valor == null ? null : valor.trim().toLowerCase(Locale.ROOT);
    }

    private static UsuarioEntity aEntidad(Usuario usuario) {
        return new UsuarioEntity(usuario.getId(), usuario.getNombreUsuario(), usuario.getCorreo(),
                usuario.getHashContrasena(), usuario.getCreadoEn(), usuario.getActualizadoEn());
    }

    private static Usuario aDominio(UsuarioEntity entidad) {
        return new Usuario(entidad.getId(), entidad.getNombreUsuario(), entidad.getCorreo(),
                entidad.getHashContrasena(), entidad.getCreadoEn(), entidad.getActualizadoEn());
    }

    private static UsuarioResultado aRespuesta(Usuario usuario) {
        return new UsuarioResultado(usuario.getId(), usuario.getNombreUsuario(),
                usuario.getCorreo(), usuario.getCreadoEn());
    }
}
