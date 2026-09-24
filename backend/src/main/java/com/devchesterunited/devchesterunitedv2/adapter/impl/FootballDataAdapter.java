package com.devchesterunited.devchesterunitedv2.adapter.impl;

import java.io.IOException;
import com.devchesterunited.devchesterunitedv2.adapter.FootballDataException;
import com.devchesterunited.devchesterunitedv2.adapter.dto.FootballCompetitionDTO;
import com.devchesterunited.devchesterunitedv2.adapter.dto.FootballPlayerDTO;
import com.devchesterunited.devchesterunitedv2.adapter.dto.FootballSeasonDTO;
import com.devchesterunited.devchesterunitedv2.adapter.dto.FootballTeamDTO;
import com.devchesterunited.devchesterunitedv2.adapter.dto.FootballTeamsResponseDTO;
import com.devchesterunited.devchesterunitedv2.modelo.CatalogoSincronizado;
import com.devchesterunited.devchesterunitedv2.modelo.Equipo;
import com.devchesterunited.devchesterunitedv2.modelo.Jugador;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.net.URI;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.client.JdkClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;

/** Adapter aislado para Football-Data.org; nunca expone sus DTOs al dominio. */
@Component
public class FootballDataAdapter {
    private static final URI BASE_PRODUCTIVA = URI.create("https://api.football-data.org/v4");
    private static final List<Liga> LIGAS = List.of(
            new Liga("PL", "Premier League", 20), new Liga("PD", "LaLiga", 20),
            new Liga("BL1", "Bundesliga", 18), new Liga("SA", "Serie A", 20),
            new Liga("FL1", "Ligue 1", 18));
    private static final long MAX_BODY = 10L * 1024 * 1024;
    private static final Duration CADENCIA = Duration.ofSeconds(6);
    private static final Duration DEADLINE = Duration.ofSeconds(120);
    private final RestClient cliente;
    private final String apiKey;
    private final ObjectMapper mapper;
    private final Clock clock;
    private final Sleeper sleeper;

    @Autowired
    public FootballDataAdapter(RestClient.Builder builder,
            @Value("${football-data.api-key:}") String apiKey, ObjectMapper mapper, Clock clock) {
        this(crearCliente(builder), apiKey, mapper, clock, duration -> Thread.sleep(duration.toMillis()));
    }

    public FootballDataAdapter(RestClient cliente, String apiKey, ObjectMapper mapper,
            Clock clock, Sleeper sleeper) {
        this.cliente = cliente;
        this.apiKey = apiKey == null ? "" : apiKey;
        this.mapper = mapper;
        this.clock = clock;
        this.sleeper = sleeper;
    }

    private void validarRespuesta(FootballTeamsResponseDTO respuesta, Liga liga) {
        if (respuesta == null
                || respuesta.competition() == null
                || !liga.codigo().equals(respuesta.competition().code())
                || respuesta.season() == null
                || respuesta.season().id() == null
                || respuesta.season().id() <= 0
                || respuesta.season().startDate() == null
                || respuesta.season().endDate() == null
                || respuesta.season().endDate().isBefore(respuesta.season().startDate())
                || respuesta.teams() == null
                || respuesta.count() == null
                || respuesta.count() != respuesta.teams().size()
                || respuesta.teams().size() != liga.cantidad()) {

            throw new FootballDataException(
                    "La respuesta del proveedor no forma un snapshot utilizable."
            );
        }
    }

    private void convertirEquipo(
            FootballTeamDTO equipoExterno,
            Liga liga,
            FootballSeasonDTO temporada,
            List<Equipo> equipos,
            List<Jugador> jugadores,
            Map<Long, UUID> equiposPorExterno,
            Map<Long, String> jugadoresVistos) {

        validarEquipo(equipoExterno);

        if (equiposPorExterno.containsKey(equipoExterno.id())) {
            throw new FootballDataException("El proveedor repitio un equipo.");
        }

        UUID idEquipo = UUID.randomUUID();
        equiposPorExterno.put(equipoExterno.id(), idEquipo);

        equipos.add(Equipo.importado(
                idEquipo,
                equipoExterno.id(),
                equipoExterno.name(),
                equipoExterno.shortName(),
                equipoExterno.tla(),
                liga.nombre(),
                uriHttps(equipoExterno.crest()),
                temporada.id(),
                temporada.startDate(),
                temporada.endDate(),
                clock.instant()
        ));

        Set<Long> idsEnEquipo = new HashSet<>();

        for (FootballPlayerDTO jugador : equipoExterno.squad()) {
            convertirJugador(
                    jugador,
                    idEquipo,
                    idsEnEquipo,
                    jugadores,
                    jugadoresVistos
            );
        }
    }

    private void convertirJugador(
            FootballPlayerDTO jugador,
            UUID idEquipo,
            Set<Long> idsEnEquipo,
            List<Jugador> jugadores,
            Map<Long, String> jugadoresVistos) {

        validarJugador(jugador);

        String firma = firma(jugador, idEquipo);

        if (!idsEnEquipo.add(jugador.id())) {
            validarJugadorDuplicado(jugador.id(), firma, jugadoresVistos);
            return;
        }

        String previa = jugadoresVistos.putIfAbsent(jugador.id(), firma);

        if (previa != null && !previa.equals(firma)) {
            throw new FootballDataException(
                    "El proveedor repitio un jugador con datos incompatibles."
            );
        }

        if (previa == null) {
            jugadores.add(Jugador.importado(
                    UUID.randomUUID(),
                    jugador.id(),
                    nombre(jugador),
                    idEquipo,
                    posicion(jugador.position()),
                    jugador.shirtNumber(),
                    jugador.nationality().trim(),
                    clock.instant()
            ));
        }
    }

    private void validarJugadorDuplicado(
            Long jugadorId,
            String firma,
            Map<Long, String> jugadoresVistos) {

        if (!firma.equals(jugadoresVistos.get(jugadorId))) {
            throw new FootballDataException(
                    "El proveedor repitio un jugador con datos incompatibles."
            );
        }
    }

    private static RestClient crearCliente(RestClient.Builder builder) {
        var http = java.net.http.HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(5))
                .followRedirects(java.net.http.HttpClient.Redirect.NEVER).build();
        var factory = new JdkClientHttpRequestFactory(http);
        factory.setReadTimeout(Duration.ofSeconds(15));
        return builder.baseUrl(BASE_PRODUCTIVA.toString()).requestFactory(factory).build();
    }

    public CatalogoSincronizado sincronizar() {
        if (apiKey.isBlank()) throw new FootballDataException("La integracion externa no esta configurada.");
        Instant limite = clock.instant().plus(DEADLINE);
        List<Equipo> equipos = new ArrayList<>();
        List<Jugador> jugadores = new ArrayList<>();
        Map<Long, UUID> equiposPorExterno = new HashMap<>();
        Map<Long, String> jugadoresVistos = new HashMap<>();
        for (int i = 0; i < LIGAS.size(); i++) {
            comprobarDeadline(limite);
            if (i > 0) dormirCadencia(limite);
            Liga liga = LIGAS.get(i);
            FootballTeamsResponseDTO respuesta = obtener(liga.codigo());
            convertir(respuesta, liga, equipos, jugadores, equiposPorExterno, jugadoresVistos);
        }
        comprobarDeadline(limite);
        try {
            return new CatalogoSincronizado(equipos, jugadores);
        } catch (IllegalArgumentException e) {
            throw new FootballDataException("La respuesta del proveedor no forma un snapshot utilizable.", e);
        }
    }

    private FootballTeamsResponseDTO obtener(String codigo) {
        try {
            return cliente.get().uri("/competitions/" + codigo + "/teams")
                    .header("X-Auth-Token", apiKey)
                    .exchange((request, response) -> {
                        HttpStatusCode status = response.getStatusCode();
                        if (!status.is2xxSuccessful()) throw new FootballDataException("El proveedor rechazo la consulta.");
                        validarCuota(response.getHeaders());
                        byte[] bytes = response.getBody().readAllBytes();
                        if (bytes.length > MAX_BODY) throw new FootballDataException("La respuesta del proveedor excede el limite.");
                        try { return mapper.readValue(bytes, FootballTeamsResponseDTO.class); }
                        catch (IOException e) { throw new FootballDataException("La respuesta del proveedor no es valida.", e); }
                    });
        } catch (FootballDataException e) { throw e; }
        catch (RestClientException e) { throw new FootballDataException("No fue posible consultar el proveedor.", e); }
    }

    private void convertir(
            FootballTeamsResponseDTO respuesta,
            Liga liga,
            List<Equipo> equipos,
            List<Jugador> jugadores,
            Map<Long, UUID> equiposPorExterno,
            Map<Long, String> jugadoresVistos) {

        validarRespuesta(respuesta, liga);

        FootballSeasonDTO temporada = respuesta.season();

        for (FootballTeamDTO equipoExterno : respuesta.teams()) {
            convertirEquipo(
                    equipoExterno,
                    liga,
                    temporada,
                    equipos,
                    jugadores,
                    equiposPorExterno,
                    jugadoresVistos
            );
        }
    }

    private static void validarEquipo(FootballTeamDTO equipo) {
        if (equipo == null || equipo.id() == null || equipo.id() <= 0 || equipo.name() == null || equipo.name().trim().isEmpty()
                || equipo.name().codePointCount(0, equipo.name().length()) > 100 || equipo.squad() == null || equipo.squad().isEmpty()) {
            throw new FootballDataException("El equipo externo no cumple el contrato.");
        }
    }

    private static void validarJugador(FootballPlayerDTO jugador) {
        if (jugador == null || jugador.id() == null || jugador.id() <= 0 || jugador.nationality() == null
                || jugador.nationality().trim().isEmpty() || jugador.nationality().codePointCount(0, jugador.nationality().length()) > 100
                || jugador.shirtNumber() != null && (jugador.shirtNumber() < 1 || jugador.shirtNumber() > 99)) {
            throw new FootballDataException("El jugador externo no cumple el contrato.");
        }
        posicion(jugador.position());
    }

    private static String nombre(FootballPlayerDTO jugador) {
        String completo = jugador.name() == null ? "" : jugador.name();
        if (completo.isBlank() || completo.codePointCount(0, completo.length()) > 200) {
            throw new FootballDataException("El nombre del jugador externo no cumple el contrato.");
        }
        return completo;
    }

    private static String posicion(String externa) {
        if (externa == null) return null;
        return switch (externa) {
            case "Goalkeeper" -> "Goalkeeper";
            case "Defence", "Defender", "Centre-Back", "Left-Back", "Right-Back" -> "Defender";
            case "Midfield", "Midfielder", "Defensive Midfield", "Central Midfield", "Attacking Midfield", "Left Midfield", "Right Midfield" -> "Midfielder";
            case "Offence", "Forward", "Centre-Forward", "Left Winger", "Right Winger", "Second Striker" -> "Forward";
            default -> throw new FootballDataException("La posicion externa no esta soportada.");
        };
    }

    private static String firma(FootballPlayerDTO j, UUID equipo) {
        return equipo + "|" + j.name() + "|" + j.shirtNumber() + "|" + j.nationality() + "|" + j.position();
    }

    private static URI uriHttps(String crest) {
        if (crest == null || crest.isBlank()) return null;
        try { URI uri = URI.create(crest.trim()); return "https".equalsIgnoreCase(uri.getScheme()) && uri.getHost() != null ? uri : null; }
        catch (IllegalArgumentException e) { return null; }
    }

    private void dormirCadencia(Instant limite) {
        comprobarDeadline(limite);
        try { sleeper.sleep(CADENCIA); } catch (InterruptedException e) { Thread.currentThread().interrupt(); throw new FootballDataException("La sincronizacion fue interrumpida.", e); }
    }
    private void comprobarDeadline(Instant limite) { if (!clock.instant().isBefore(limite)) throw new FootballDataException("Se excedio el presupuesto de sincronizacion."); }
    private static void validarCuota(HttpHeaders headers) {
        String disponible = headers.getFirst("X-RequestsAvailable");
        if (disponible != null) { try { if (Long.parseLong(disponible) <= 0) throw new FootballDataException("La cuota del proveedor no esta disponible."); } catch (NumberFormatException e) { throw new FootballDataException("La cuota del proveedor es invalida."); } }
    }
    @FunctionalInterface public interface Sleeper { void sleep(Duration duration) throws InterruptedException; }
    private record Liga(String codigo, String nombre, int cantidad) { }
}
