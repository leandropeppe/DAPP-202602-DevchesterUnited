package com.devchesterunited.devchesterunitedv2.scraping.catalogo;

import com.devchesterunited.devchesterunitedv2.modelo.equipo.Equipo;
import com.devchesterunited.devchesterunitedv2.modelo.equipo.IdentidadExternaEquipo;
import com.devchesterunited.devchesterunitedv2.modelo.equipo.TemporadaEquipo;
import com.devchesterunited.devchesterunitedv2.modelo.jugador.CatalogoSincronizado;
import com.devchesterunited.devchesterunitedv2.modelo.jugador.Jugador;
import com.devchesterunited.devchesterunitedv2.modelo.jugador.ProveedorIdentidadExterna;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.devchesterunited.devchesterunitedv2.scraping.catalogo.dto.FootballTeamDetailsDTO;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.client.JdkClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;

import java.io.IOException;
import java.net.URI;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.*;

/** Adapter aislado para Football-Data.org; nunca expone sus DTOs al dominio. */
@Component
public class FootballDataAdapter implements ProveedorCatalogo {
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
            ConversionContext contexto) {

        validarEquipo(equipoExterno);

        if (contexto.equiposPorExterno().containsKey(equipoExterno.id())) {
            throw new FootballDataException("El proveedor repitio un equipo.");
        }

        UUID idEquipo = UUID.randomUUID();
        contexto.equiposPorExterno().put(equipoExterno.id(), idEquipo);

        contexto.equipos().add(Equipo.importado(
                idEquipo,
                equipoExterno.name(),
                liga.nombre(),
                new TemporadaEquipo(
                        temporada.id(), temporada.startDate(), temporada.endDate()),
                clock.instant()
        ));
        contexto.identidadesExternasEquipos().add(new IdentidadExternaEquipo(
                idEquipo, ProveedorIdentidadExterna.FOOTBALL_DATA, equipoExterno.id()));

        Set<Long> idsEnEquipo = new HashSet<>();
        JugadorConversionContext jugadorContext = new JugadorConversionContext(
                equipoExterno.id(), idsEnEquipo, contexto.jugadores(), contexto.jugadoresVistos(),
                contexto.identidadesFootballData(), contexto.limite());

        for (FootballPlayerDTO jugador : equipoExterno.squad()) {
            convertirJugador(jugador, idEquipo, jugadorContext);
        }
    }

    private void convertirJugador(FootballPlayerDTO jugador, UUID idEquipo,
            JugadorConversionContext contexto) {

        validarJugador(jugador);

        String firma = firma(jugador);

        if (!contexto.idsEnEquipo().add(jugador.id())) {
            validarJugadorDuplicado(jugador.id(), firma, contexto.jugadoresVistos());
            return;
        }

        JugadorVisto previa = contexto.jugadoresVistos().get(jugador.id());

        if (previa != null && !previa.firma().equals(firma)) {
            throw new FootballDataException(
                    "El proveedor repitio un jugador con datos incompatibles."
            );
        }

        if (previa == null) {
            Jugador jugadorInterno = crearJugador(jugador, idEquipo);
            contexto.jugadores().add(jugadorInterno);
            contexto.identidadesFootballData().put(jugadorInterno.getId(), jugador.id());
            contexto.jugadoresVistos().put(jugador.id(),
                    new JugadorVisto(firma, contexto.equipoExternoId(), jugadorInterno));
        } else if (!previa.equipoExternoId().equals(contexto.equipoExternoId())) {
            reconciliarDuplicado(jugador, idEquipo, previa, contexto);
        }
    }

    private Jugador crearJugador(FootballPlayerDTO jugador, UUID idEquipo) {
        return new Jugador(UUID.randomUUID(), nombre(jugador), idEquipo,
                posicion(jugador.position()), jugador.shirtNumber(),
                jugador.nationality().trim(), clock.instant());
    }

    private void reconciliarDuplicado(FootballPlayerDTO jugador, UUID idEquipo,
            JugadorVisto previa, JugadorConversionContext contexto) {
        Instant anterior = obtenerEquipo(previa.equipoExternoId(), contexto.limite());
        Instant nuevo = obtenerEquipo(contexto.equipoExternoId(), contexto.limite());
        if (nuevo.equals(anterior)) {
            throw new FootballDataException("No fue posible resolver el equipo vigente del jugador repetido.");
        }
        if (nuevo.isAfter(anterior)) {
            Jugador reemplazo = crearJugador(jugador, idEquipo);
            int indice = contexto.jugadores().indexOf(previa.jugador());
            if (indice < 0) {
                throw new FootballDataException("No fue posible reconciliar el jugador repetido.");
            }
            contexto.jugadores().set(indice, reemplazo);
            contexto.identidadesFootballData().remove(previa.jugador().getId());
            contexto.identidadesFootballData().put(reemplazo.getId(), jugador.id());
            contexto.jugadoresVistos().put(jugador.id(),
                    new JugadorVisto(previa.firma(), contexto.equipoExternoId(), reemplazo));
        }
    }

    private void validarJugadorDuplicado(
            Long jugadorId,
            String firma,
            Map<Long, JugadorVisto> jugadoresVistos) {

        JugadorVisto visto = jugadoresVistos.get(jugadorId);
        if (visto == null || !firma.equals(visto.firma())) {
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

    @Override
    public CatalogoSincronizado sincronizar() {
        if (apiKey.isBlank()) throw new FootballDataException("La integracion externa no esta configurada.");
        Instant limite = clock.instant().plus(DEADLINE);
        List<Equipo> equipos = new ArrayList<>();
        List<Jugador> jugadores = new ArrayList<>();
        Map<Long, UUID> equiposPorExterno = new HashMap<>();
        List<IdentidadExternaEquipo> identidadesExternasEquipos = new ArrayList<>();
        Map<Long, JugadorVisto> jugadoresVistos = new HashMap<>();
        Map<UUID, Long> identidadesFootballData = new HashMap<>();
        ConversionContext contexto = new ConversionContext(
                equipos, jugadores, equiposPorExterno, identidadesExternasEquipos,
                jugadoresVistos, identidadesFootballData, limite);
        for (int i = 0; i < LIGAS.size(); i++) {
            comprobarDeadline(limite);
            if (i > 0) dormirCadencia(limite);
            Liga liga = LIGAS.get(i);
            FootballTeamsResponseDTO respuesta = obtener(liga.codigo());
            convertir(respuesta, liga, contexto);
        }
        comprobarDeadline(limite);
        try {
            return new CatalogoSincronizado(equipos, jugadores, identidadesFootballData,
                    identidadesExternasEquipos);
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

    private Instant obtenerEquipo(Long equipoId, Instant limite) {
        comprobarDeadline(limite);
        try {
            return cliente.get().uri("/teams/" + equipoId)
                    .header("X-Auth-Token", apiKey)
                    .exchange((request, response) -> {
                        HttpStatusCode status = response.getStatusCode();
                        if (!status.is2xxSuccessful()) throw new FootballDataException("El proveedor rechazo la consulta.");
                        validarCuota(response.getHeaders());
                        byte[] bytes = response.getBody().readAllBytes();
                        if (bytes.length > MAX_BODY) throw new FootballDataException("La respuesta del proveedor excede el limite.");
                        try {
                            FootballTeamDetailsDTO equipo = mapper.readValue(bytes, FootballTeamDetailsDTO.class);
                            if (equipo == null || !equipoId.equals(equipo.id())
                                    || equipo.lastUpdated() == null || equipo.lastUpdated().isBlank()) {
                                throw new FootballDataException("La respuesta del proveedor no permite resolver el jugador repetido.");
                            }
                            return Instant.parse(equipo.lastUpdated());
                        } catch (FootballDataException e) { throw e; }
                        catch (Exception e) {
                            throw new FootballDataException("La respuesta del proveedor no permite resolver el jugador repetido.", e);
                        }
                    });
        } catch (FootballDataException e) { throw e; }
        catch (RestClientException e) { throw new FootballDataException("No fue posible consultar el proveedor.", e); }
    }

    private void convertir(
            FootballTeamsResponseDTO respuesta,
            Liga liga,
            ConversionContext contexto) {

        validarRespuesta(respuesta, liga);

        FootballSeasonDTO temporada = respuesta.season();

        for (FootballTeamDTO equipoExterno : respuesta.teams()) {
            convertirEquipo(
                    equipoExterno,
                    liga,
                    temporada,
                    contexto
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
        if (externa == null || externa.isBlank() || "null".equalsIgnoreCase(externa.trim())) {
            return null;
        }

        return switch (externa.trim()) {
            case "Goalkeeper" -> "Goalkeeper";
            case "Defence", "Defender", "Centre-Back", "Left-Back", "Right-Back" -> "Defender";
            case "Midfield", "Midfielder", "Defensive Midfield", "Central Midfield", "Attacking Midfield", "Left Midfield", "Right Midfield" -> "Midfielder";
            case "Offence", "Forward", "Centre-Forward", "Left Winger", "Right Winger", "Second Striker" -> "Forward";
            default -> throw new FootballDataException("La posicion externa no esta soportada.");
        };
    }

    private static String firma(FootballPlayerDTO j) {
        return j.name() + "|" + j.shirtNumber() + "|" + j.nationality() + "|" + j.position();
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
    private record ConversionContext(
            List<Equipo> equipos,
            List<Jugador> jugadores,
            Map<Long, UUID> equiposPorExterno,
            List<IdentidadExternaEquipo> identidadesExternasEquipos,
            Map<Long, JugadorVisto> jugadoresVistos,
            Map<UUID, Long> identidadesFootballData,
            Instant limite) { }
    private record JugadorConversionContext(
            Long equipoExternoId,
            Set<Long> idsEnEquipo,
            List<Jugador> jugadores,
            Map<Long, JugadorVisto> jugadoresVistos,
            Map<UUID, Long> identidadesFootballData,
            Instant limite) { }
    private record JugadorVisto(String firma, Long equipoExternoId, Jugador jugador) { }
    private record Liga(String codigo, String nombre, int cantidad) { }
}
