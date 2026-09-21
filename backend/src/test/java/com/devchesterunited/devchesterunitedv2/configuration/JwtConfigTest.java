package com.devchesterunited.devchesterunitedv2.configuration;

import com.nimbusds.jose.JWSAlgorithm;
import com.nimbusds.jose.JWSHeader;
import com.nimbusds.jose.crypto.MACSigner;
import com.nimbusds.jose.crypto.MACVerifier;
import com.nimbusds.jwt.JWTClaimsSet;
import com.nimbusds.jwt.SignedJWT;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.util.Base64;
import java.util.Date;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.NullAndEmptySource;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.boot.test.system.CapturedOutput;
import org.springframework.boot.test.system.OutputCaptureExtension;
import org.springframework.mock.env.MockEnvironment;
import org.springframework.security.oauth2.jwt.JwtException;
import org.springframework.security.oauth2.jwt.NimbusJwtDecoder;
import org.springframework.security.oauth2.jwt.NimbusJwtEncoder;
import static org.assertj.core.api.Assertions.*;

/** Claves aleatorias exclusivas de la prueba: nunca se guardan ni se imprimen. */
@ExtendWith(OutputCaptureExtension.class)
class JwtConfigTest {
    private static final Instant AHORA = Instant.parse("2026-09-06T12:00:00Z");
    private static final UUID ID = UUID.fromString("12345678-1234-1234-1234-123456789abc");
    private final byte[] clave = clave(64);
    private final RelojControlable reloj = new RelojControlable(AHORA);
    private final JwtConfig config = configurar(clave, reloj);

    @Test
    void generaFirmaHS256VerificableYClaimsContractuales() throws Exception {
        var emitido = config.jwtEncoder().encode(config.parametrosPara(ID));
        var firmado = SignedJWT.parse(emitido.getTokenValue());
        assertThat(firmado.getHeader().getAlgorithm()).isEqualTo(JWSAlgorithm.HS256);
        assertThat(firmado.verify(new MACVerifier(clave))).isTrue();
        var jwt = config.jwtDecoder().decode(emitido.getTokenValue());
        assertThat(jwt.getClaimAsString("iss")).isEqualTo("devchester-united");
        assertThat(jwt.getAudience()).containsExactly("devchester-api");
        assertThat(jwt.getSubject()).isEqualTo("usr_" + ID);
        assertThat(jwt.getIssuedAt()).isEqualTo(AHORA);
        assertThat(jwt.getExpiresAt()).isEqualTo(AHORA.plusSeconds(3600));
        assertThat(Duration.between(jwt.getIssuedAt(), jwt.getExpiresAt())).isEqualTo(Duration.ofSeconds(3600));
    }

    @Test
    void encoderYDecoderSonBeansNimbusConClockInyectado() {
        String secreto = Base64.getEncoder().encodeToString(clave);
        new ApplicationContextRunner().withUserConfiguration(JwtConfig.class)
                .withBean(Clock.class, () -> reloj).withPropertyValues("jwt.secret=" + secreto)
                .run(contexto -> {
                    assertThat(contexto).hasNotFailed().hasSingleBean(NimbusJwtEncoder.class).hasSingleBean(NimbusJwtDecoder.class);
                    var parametros = contexto.getBean(JwtConfig.class).parametrosPara(ID);
                    String token = contexto.getBean(NimbusJwtEncoder.class).encode(parametros).getTokenValue();
                    assertThat(contexto.getBean(NimbusJwtDecoder.class).decode(token).getIssuedAt()).isEqualTo(AHORA);
                });
    }

    @Test
    void relojControlaEmisionYExpiracionExactaSinTolerancia() {
        var decoder = config.jwtDecoder();
        String token = config.jwtEncoder().encode(config.parametrosPara(ID)).getTokenValue();
        reloj.ahora = AHORA.minusNanos(1);
        assertThatThrownBy(() -> decoder.decode(token)).isInstanceOf(JwtException.class);
        reloj.ahora = AHORA;
        assertThatCode(() -> decoder.decode(token)).doesNotThrowAnyException();
        reloj.ahora = AHORA.plusSeconds(3600).minusNanos(1);
        assertThatCode(() -> decoder.decode(token)).doesNotThrowAnyException();
        reloj.ahora = AHORA.plusSeconds(3600);
        assertThatThrownBy(() -> decoder.decode(token)).isInstanceOf(JwtException.class);
        reloj.ahora = AHORA.plusSeconds(3601);
        assertThatThrownBy(() -> decoder.decode(token)).isInstanceOf(JwtException.class);
        var nuevo = config.jwtEncoder().encode(config.parametrosPara(ID));
        assertThat(decoder.decode(nuevo.getTokenValue()).getIssuedAt()).isEqualTo(reloj.instant());
    }

    @Test
    void emisionUsaSegundosEnterosSinAlterarDuracion() {
        reloj.ahora = AHORA.plusNanos(123456789);
        var jwt = config.jwtDecoder().decode(config.jwtEncoder().encode(config.parametrosPara(ID)).getTokenValue());
        assertThat(jwt.getIssuedAt()).isEqualTo(AHORA);
        assertThat(jwt.getExpiresAt()).isEqualTo(AHORA.plusSeconds(3600));
        assertThatThrownBy(() -> config.parametrosPara(null)).isInstanceOf(IllegalArgumentException.class);
    }

    @ParameterizedTest
    @ValueSource(strings = {"iss", "aud", "sub", "iat", "exp"})
    void rechazaClaimsObligatoriosAusentes(String campo) throws Exception {
        var claims = claims().claim(campo, null).build();
        String token = firmar(claims, JWSAlgorithm.HS256, clave);
        var decoder = config.jwtDecoder();
        assertThatThrownBy(() -> decoder.decode(token)).isInstanceOf(JwtException.class);
    }

    @ParameterizedTest
    @ValueSource(strings = {"emisor", "audiencia", "sujetoSinPrefijo", "uuidInvalido", "sujetoVacio",
            "iatFuturo", "duracionCorta", "duracionLarga", "expirado", "nbfFuturo"})
    void rechazaClaimsIncorrectosAunqueFirmaSeaValida(String caso) throws Exception {
        var claims = claims();
        switch (caso) {
            case "emisor" -> claims.issuer("otro-emisor");
            case "audiencia" -> claims.audience("otra-audiencia");
            case "sujetoSinPrefijo" -> claims.subject(ID.toString());
            case "uuidInvalido" -> claims.subject("usr_no-es-uuid");
            case "sujetoVacio" -> claims.subject("");
            case "iatFuturo" -> claims.issueTime(Date.from(AHORA.plusSeconds(1))).expirationTime(Date.from(AHORA.plusSeconds(3601)));
            case "duracionCorta" -> claims.expirationTime(Date.from(AHORA.plusSeconds(3599)));
            case "duracionLarga" -> claims.expirationTime(Date.from(AHORA.plusSeconds(3601)));
            case "expirado" -> claims.issueTime(Date.from(AHORA.minusSeconds(3600))).expirationTime(Date.from(AHORA));
            case "nbfFuturo" -> claims.notBeforeTime(Date.from(AHORA.plusSeconds(1)));
            default -> throw new AssertionError("Caso desconocido");
        }
        String token = firmar(claims.build(), JWSAlgorithm.HS256, clave);
        var decoder = config.jwtDecoder();
        assertThatThrownBy(() -> decoder.decode(token)).isInstanceOf(JwtException.class);
    }

    @ParameterizedTest
    @ValueSource(strings = {"HS384", "HS512"})
    void rechazaOtrosAlgoritmosInclusoConLaMismaClave(String algoritmo) throws Exception {
        String token = firmar(claims().build(), JWSAlgorithm.parse(algoritmo), clave);
        var decoder = config.jwtDecoder();
        assertThatThrownBy(() -> decoder.decode(token))
                .isInstanceOf(JwtException.class);
        var cabecera = org.springframework.security.oauth2.jwt.JwsHeader
                .with(org.springframework.security.oauth2.jose.jws.MacAlgorithm.from(algoritmo))
                .build();
        var parametros = org.springframework.security.oauth2.jwt.JwtEncoderParameters
                .from(cabecera, config.parametrosPara(ID).getClaims());
        var encoder = config.jwtEncoder();
        assertThatThrownBy(() -> encoder.encode(parametros))
                .isInstanceOf(JwtException.class);
    }

    @Test
    void rechazaFirmaAjenaPayloadManipuladoYTokenSinFirma() throws Exception {
        var decoder = config.jwtDecoder();

        String ajeno = firmar(claims().build(), JWSAlgorithm.HS256, clave(64));
        assertThatThrownBy(() -> decoder.decode(ajeno))
                .isInstanceOf(JwtException.class);

        String token = firmar(claims().build(), JWSAlgorithm.HS256, clave);
        String[] partes = token.split("\\.");

        String payload = Base64.getUrlEncoder().withoutPadding().encodeToString(
                claims().subject("usr_00000000-0000-0000-0000-000000000000")
                        .build()
                        .toString()
                        .getBytes(java.nio.charset.StandardCharsets.UTF_8)
        );

        String manipulado = partes[0] + "." + payload + "." + partes[2];

        assertThatThrownBy(() -> decoder.decode(manipulado))
                .isInstanceOf(JwtException.class);

        String sinFirma = new com.nimbusds.jwt.PlainJWT(claims().build()).serialize();

        assertThatThrownBy(() -> decoder.decode(sinFirma))
                .isInstanceOf(JwtException.class);
    }

    @ParameterizedTest
    @NullAndEmptySource
    @ValueSource(strings = {" ", "no-es-base64!", "%%%", "YQ==", "AAAA"})
    void rechazaSecretoAusenteInvalidoOCorto(String secreto) {
        var entorno = new MockEnvironment();
        if (secreto != null) entorno.setProperty("jwt.secret", secreto);
        assertThatThrownBy(() -> new JwtConfig(entorno, reloj)).isInstanceOf(IllegalStateException.class)
                .hasMessage("La configuración JWT requiere un secreto Base64 válido de al menos 32 bytes.")
                .hasNoCause();
    }

    @Test
    void exige32BytesDecodificadosNo32CaracteresBase64() {
        var claveInvalida = clave(31);

        assertThatThrownBy(() -> configurar(claveInvalida, reloj))
                .isInstanceOf(IllegalStateException.class);

        var minimo = configurar(clave(32), reloj);

        var encoder = minimo.jwtEncoder();
        var decoder = minimo.jwtDecoder();
        var parametros = minimo.parametrosPara(ID);
        var token = encoder.encode(parametros).getTokenValue();

        assertThatCode(() -> decoder.decode(token))
                .doesNotThrowAnyException();
    }

    @Test
    void configuracionInseguraImpideArranqueSinFiltrarSecreto(CapturedOutput salida) {
        String corto = Base64.getEncoder().encodeToString(clave(31));
        new ApplicationContextRunner().withUserConfiguration(JwtConfig.class).withBean(Clock.class, () -> reloj)
                .withPropertyValues("jwt.secret=" + corto).run(contexto -> {
                    assertThat(contexto).hasFailed();
                    assertThat(contexto.getStartupFailure()).hasRootCauseInstanceOf(IllegalStateException.class);
                    var traza = new java.io.StringWriter();
                    contexto.getStartupFailure().printStackTrace(new java.io.PrintWriter(traza));
                    assertThat(traza.toString()).doesNotContain(corto);
                });
        assertThat(salida.getAll()).doesNotContain(corto);
        String secreto = Base64.getEncoder().encodeToString(clave);
        assertThat(config.toString()).doesNotContain(secreto);
        assertThat(config.jwtEncoder().toString()).doesNotContain(secreto);
        assertThat(config.jwtDecoder().toString()).doesNotContain(secreto);
    }

    private static JwtConfig configurar(byte[] clave, Clock clock) {
        return new JwtConfig(new MockEnvironment().withProperty("jwt.secret", Base64.getEncoder().encodeToString(clave)), clock);
    }

    private static byte[] clave(int longitud) {
        byte[] bytes = new byte[longitud];
        new java.security.SecureRandom().nextBytes(bytes);
        return bytes;
    }

    private static JWTClaimsSet.Builder claims() {
        return new JWTClaimsSet.Builder().issuer("devchester-united").audience("devchester-api")
                .subject("usr_" + ID).issueTime(Date.from(AHORA)).expirationTime(Date.from(AHORA.plusSeconds(3600)));
    }

    private static String firmar(JWTClaimsSet claims, JWSAlgorithm algoritmo, byte[] clave) throws Exception {
        var jwt = new SignedJWT(new JWSHeader(algoritmo), claims);
        jwt.sign(new MACSigner(clave));
        return jwt.serialize();
    }

    private static final class RelojControlable extends Clock {
        private Instant ahora;
        private RelojControlable(Instant ahora) { this.ahora = ahora; }
        @Override public ZoneId getZone() { return ZoneOffset.UTC; }
        @Override public Clock withZone(ZoneId zona) { return Clock.fixed(ahora, zona); }
        @Override public Instant instant() { return ahora; }
    }
}
