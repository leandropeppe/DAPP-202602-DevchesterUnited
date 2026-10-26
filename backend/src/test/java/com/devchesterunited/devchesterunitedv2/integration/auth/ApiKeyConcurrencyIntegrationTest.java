package com.devchesterunited.devchesterunitedv2.integration.auth;
import static org.assertj.core.api.Assertions.*;


import com.devchesterunited.devchesterunitedv2.support.PostgresTestSupport;
import com.devchesterunited.devchesterunitedv2.persistence.auth.repository.UsuarioRepository;
import com.devchesterunited.devchesterunitedv2.service.interfaces.auth.ApiKeyService;
import com.devchesterunited.devchesterunitedv2.service.dto.auth.ApiKeyResultado;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.Callable;
import java.util.concurrent.CyclicBarrier;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.dao.ConcurrencyFailureException;
import org.springframework.jdbc.core.JdbcTemplate;

@SpringBootTest
class ApiKeyConcurrencyIntegrationTest extends PostgresTestSupport {
    @Autowired ApiKeyService servicio;
    @Autowired UsuarioRepository usuarios;
    @Autowired JdbcTemplate jdbc;
    private final List<UUID> fixtures = new java.util.ArrayList<>();

    @Test
    void mismoUsuarioTieneUnGanadorYCompetidorSinMutacionTrasCommit() throws Exception {
        UUID usuario = crearUsuario();
        var barrera = new CyclicBarrier(2);
        try (var executor = Executors.newFixedThreadPool(2)) {
            Callable<Resultado> llamada = () -> {
                awaitBarrier(barrera, java.time.Duration.ofSeconds(10));
                try {
                    return Resultado.exito(servicio.generar(usuario));
                } catch (ConcurrencyFailureException | org.springframework.dao.DataIntegrityViolationException conflicto) {
                    return Resultado.conflictoResultado();
                }
            };
            var a = executor.submit(llamada);
            var b = executor.submit(llamada);
            var resultados = List.of(a.get(30, TimeUnit.SECONDS), b.get(30, TimeUnit.SECONDS));
            assertThat(resultados.stream().filter(Resultado::esExito).count()).isEqualTo(1);
            assertThat(resultados.stream().filter(Resultado::esConflicto).count()).isEqualTo(1);
            assertThat(jdbc.queryForObject("SELECT count(*) FROM api_keys WHERE user_id = ?", Long.class, usuario)).isEqualTo(1L);
            assertThat(jdbc.queryForObject("SELECT count(*) FROM api_keys WHERE user_id = ? AND is_active", Long.class, usuario)).isEqualTo(1L);
            var posterior = servicio.generar(usuario);
            assertThat(posterior.apiKey()).isNotEqualTo(resultados.stream().filter(Resultado::esExito).findFirst().orElseThrow().secreto());
            assertThat(jdbc.queryForObject("SELECT count(*) FROM api_keys WHERE user_id = ? AND is_active", Long.class, usuario)).isEqualTo(1L);
        }
    }

    @Test
    void usuariosDistintosOperanIndependientemente() throws Exception {
        UUID primero = crearUsuario();
        UUID segundo = crearUsuario();
        var barrera = new CyclicBarrier(2);
        try (var executor = Executors.newFixedThreadPool(2)) {
            var a = executor.submit(() -> generarTrasBarrera(barrera, primero));
            var b = executor.submit(() -> generarTrasBarrera(barrera, segundo));
            assertThat(List.of(a.get(30, TimeUnit.SECONDS), b.get(30, TimeUnit.SECONDS))).allMatch(Resultado::esExito);
            assertThat(jdbc.queryForObject("SELECT count(*) FROM api_keys WHERE user_id IN (?, ?)", Long.class, primero, segundo)).isEqualTo(2L);
        }
    }

    private Resultado generarTrasBarrera(CyclicBarrier barrera, UUID usuario) throws Exception {
        awaitBarrier(barrera, java.time.Duration.ofSeconds(10));
        return Resultado.exito(servicio.generar(usuario));
    }

    private UUID crearUsuario() {
        UUID id = UUID.randomUUID();
        String nombre = "u" + id.toString().replace("-", "").substring(0, 20);
        usuarios.saveAndFlush(new com.devchesterunited.devchesterunitedv2.persistence.auth.entity.UsuarioEntity(id, nombre, nombre + "@example.com", "$2a$12$R9h/cIPz0gi.URNNX3kh2OPST9/PgBkqquzi.Ss7KIUgO2t0jWMUW", Instant.now(), Instant.now()));
        fixtures.add(id);
        return id;
    }

    @AfterEach void limpiar() { fixtures.forEach(id -> jdbc.update("DELETE FROM usuarios WHERE id = ?", id)); }

    private record Resultado(ApiKeyResultado respuesta, String secreto, boolean conflicto) {
        static Resultado exito(ApiKeyResultado respuesta) {
            return new Resultado(respuesta, respuesta.apiKey(), false);
        }
        static Resultado conflictoResultado() { return new Resultado(null, null, true); }
        boolean esExito() { return !conflicto; }
        boolean esConflicto() { return conflicto; }
    }
}
