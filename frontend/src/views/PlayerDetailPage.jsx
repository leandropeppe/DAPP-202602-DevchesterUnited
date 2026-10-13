import { useEffect, useState } from 'react'
import StatusMessage from '../components/StatusMessage.jsx'
import { getPlayerDetail } from '../api/http.js'
import { errorMessage } from './formUtils.js'

const STATISTICS = [
  ['minutes_played', 'Minutos jugados', 'clock'],
  ['goals', 'Goles', 'ball'],
  ['assists', 'Asistencias', 'assist'],
  ['shots', 'Tiros', 'target'],
  ['key_passes', 'Pases clave', 'pass'],
  ['dribbles', 'Regates', 'dribble'],
  ['tackles', 'Tackles', 'shield'],
  ['rating', 'Valoración', 'star'],
  ['yellow_cards', 'Tarjetas amarillas', 'yellow-card'],
  ['red_cards', 'Tarjetas rojas', 'red-card']
]

const COUNTRY_CODES = {
  Argentina: 'AR',
  England: 'GB',
  Spain: 'ES',
  Italy: 'IT',
  Belgium: 'BE',
  Finland: 'FI',
  Germany: 'DE',
  France: 'FR'
}

const POSITION_THEMES = {
  Goalkeeper: 'green',
  Defender: 'red',
  Midfielder: 'blue',
  Forward: 'purple'
}

const POSITION_ABBREVIATIONS = {
  Goalkeeper: 'PO',
  Defender: 'DF',
  Midfielder: 'MC',
  Forward: 'DL'
}

function displayValue (value) {
  if (value === null || value === undefined || value === '') {
    return 'Sin información'
  }

  return value
}

function formatNumber (value) {
  if (typeof value !== 'number') {
    return displayValue(value)
  }

  return value.toLocaleString('es-AR', {
    maximumFractionDigits: 2
  })
}

function getPlayerTheme (player) {
  return POSITION_THEMES[player.position] ?? 'neutral'
}

function getPositionAbbreviation (position) {
  return POSITION_ABBREVIATIONS[position] ??
      position?.slice(0, 2).toUpperCase() ??
      '--'
}

function PlayerSilhouette () {
  return (
      <svg
          className="detail-player-card__silhouette"
          viewBox="0 0 300 300"
          aria-hidden="true"
      >
        <path
            d="
          M150 42
          C119 42 100 66 102 96
          C103 118 115 136 128 145
          L125 161
          C102 168 76 180 63 204
          C53 222 47 249 44 275
          L256 275
          C253 249 247 222 237 204
          C224 180 198 168 175 161
          L172 145
          C185 136 197 118 198 96
          C200 66 181 42 150 42
          Z
        "
        />

        <path
            className="detail-player-card__silhouette-highlight"
            d="
          M121 162
          C130 173 139 178 150 178
          C161 178 170 173 179 162
          L192 168
          C181 194 168 205 150 205
          C132 205 119 194 108 168
          Z
        "
        />
      </svg>
  )
}

function ShieldIcon () {
  return (
      <svg
          viewBox="0 0 24 24"
          aria-hidden="true"
      >
        <path d="M12 2 20 5v6c0 5.3-3.4 9.3-8 11-4.6-1.7-8-5.7-8-11V5l8-3Z" />
        <path d="M8 9h8M9 13h6" />
      </svg>
  )
}

function TrophyIcon () {
  return (
      <svg
          viewBox="0 0 24 24"
          aria-hidden="true"
      >
        <path
            d="
          M8 3h8v3h3v3
          c0 3-2 5-5 5h-1v3h4v2H7v-2h4v-3h-1
          c-3 0-5-2-5-5V6h3V3Z
        "
        />
      </svg>
  )
}

function DetailIcon ({ type }) {
  switch (type) {
    case 'clock':
      return <span aria-hidden="true">◷</span>

    case 'ball':
      return <span aria-hidden="true">●</span>

    case 'assist':
      return <span aria-hidden="true">↗</span>

    case 'target':
      return <span aria-hidden="true">◎</span>

    case 'pass':
      return <span aria-hidden="true">⌁</span>

    case 'dribble':
      return <span aria-hidden="true">↝</span>

    case 'shield':
      return <span aria-hidden="true">◇</span>

    case 'star':
      return <span aria-hidden="true">☆</span>

    case 'yellow-card':
      return (
          <span
              className="stat-icon-card stat-icon-card--yellow"
              aria-hidden="true"
          />
      )

    case 'red-card':
      return (
          <span
              className="stat-icon-card stat-icon-card--red"
              aria-hidden="true"
          />
      )

    default:
      return null
  }
}

export default function PlayerDetailPage ({ playerId, onNavigate }) {
  const [state, setState] = useState({
    status: 'loading',
    message: ''
  })

  const [player, setPlayer] = useState(null)

  useEffect(() => {
    let active = true

    async function loadPlayer () {
      setState({
        status: 'loading',
        message: ''
      })

      try {
        const response = await getPlayerDetail(playerId)

        if (!active) return

        setPlayer(response?.data || null)

        setState({
          status: 'success',
          message: ''
        })
      } catch (error) {
        if (!active) return

        setState({
          status: 'error',
          message: errorMessage(error)
        })
      }
    }

    loadPlayer()

    return () => {
      active = false
    }
  }, [playerId])

  function goBack (event) {
    event.preventDefault()
    onNavigate('/catalogo')
  }

  if (state.status === 'loading') {
    return (
        <StatusMessage type="info">
          Cargando detalle del jugador…
        </StatusMessage>
    )
  }

  if (state.status === 'error' || !player) {
    return (
        <section
            className="player-detail"
            aria-labelledby="player-detail-error-title"
        >
          <h1 id="player-detail-error-title">
            No se pudo cargar el jugador
          </h1>

          <StatusMessage type="error">
            {state.message || 'Jugador no encontrado.'}
          </StatusMessage>

          <a
              className="button button--secondary player-detail__back"
              href="/catalogo"
              onClick={goBack}
          >
            Volver al catálogo
          </a>
        </section>
    )
  }

  const statistics = player.statistics
  const theme = getPlayerTheme(player)
  const countryCode = COUNTRY_CODES[player.nationality]

  return (
      <section
          className="player-detail"
          aria-labelledby="player-detail-title"
      >
        <div className="player-detail__hero">
          <div
              className="player-detail__stadium"
              aria-hidden="true"
          />

          <a
              className="player-detail__back-link"
              href="/catalogo"
              onClick={goBack}
          >
            ← Volver al catálogo
          </a>

          <div className="player-detail__heading">
            <p className="eyebrow">
              Detalle del jugador
            </p>

            <h1 id="player-detail-title">
              {player.name}
            </h1>

            <p>
              {displayValue(player.team)}
              {' · '}
              {displayValue(player.league)}
            </p>
          </div>

          <div className="player-detail__hero-layout">
            <article
                className={`detail-player-card detail-player-card--${theme}`}
            >
              <div className="detail-player-card__rating">
                <strong>-</strong>

                <span>
                {getPositionAbbreviation(player.position)}
              </span>
              </div>

              <div className="detail-player-card__portrait">
                <PlayerSilhouette />
              </div>

              <div className="detail-player-card__content">
                <h2>
                  {player.name}
                </h2>

                <div className="detail-player-card__metadata">
                  <div>
                  <span className="detail-player-card__icon detail-player-card__icon--outline">
                    <ShieldIcon />
                  </span>

                    <span>
                    {displayValue(player.team)}
                  </span>
                  </div>

                  <div>
                  <span className="detail-player-card__icon">
                    <TrophyIcon />
                  </span>

                    <span>
                    {displayValue(player.league)}
                  </span>
                  </div>

                  <div>
                    {countryCode
                        ? (
                            <img
                                src={`https://flagcdn.com/${countryCode.toLowerCase()}.svg`}
                                alt=""
                                className="detail-player-card__flag"
                            />
                        )
                        : (
                            <span className="detail-player-card__icon">
                        ◉
                      </span>
                        )}

                    <span>
                    {displayValue(player.nationality)}
                  </span>
                  </div>
                </div>
              </div>
            </article>

            <div className="player-detail__overview">
              <div className="player-detail__identity-grid">
                <div className="detail-info-card">
                <span className="detail-info-card__icon">
                  ◇
                </span>

                  <div>
                  <span className="player-detail__label">
                    Posición
                  </span>

                    <strong>
                      {displayValue(player.position)}
                    </strong>
                  </div>
                </div>

                <div className="detail-info-card">
                <span className="detail-info-card__icon">
                  {countryCode
                      ? (
                          <img
                              src={`https://flagcdn.com/${countryCode.toLowerCase()}.svg`}
                              alt=""
                              className="detail-info-card__flag"
                          />
                      )
                      : (
                          <span aria-hidden="true">
                        ●
                      </span>
                      )}
                </span>

                  <div>
                  <span className="player-detail__label">
                    Nacionalidad
                  </span>

                    <strong>
                      {displayValue(player.nationality)}
                    </strong>
                  </div>
                </div>

                <div className="detail-info-card detail-info-card--valuation">
                <span className="detail-info-card__icon">
                  ◉
                </span>

                  <div>
                  <span className="player-detail__label">
                    Valorización Devchester
                  </span>

                    <strong>
                      -
                    </strong>
                  </div>
                </div>
              </div>

              {statistics
                  ? (
                      <div className="player-detail__highlights">
                        <div className="highlight-stat">
                    <span>
                      Minutos jugados
                    </span>

                          <strong>
                            {formatNumber(statistics.minutes_played)}
                          </strong>
                        </div>

                        <div className="highlight-stat">
                    <span>
                      Goles
                    </span>

                          <strong>
                            {formatNumber(statistics.goals)}
                          </strong>
                        </div>

                        <div className="highlight-stat">
                    <span>
                      Valoración
                    </span>

                          <strong>
                            {formatNumber(statistics.rating)}
                          </strong>
                        </div>
                      </div>
                  )
                  : null}
            </div>
          </div>
        </div>

        {statistics
            ? (
                <section
                    className="player-statistics"
                    aria-labelledby="statistics-title"
                >
                  <div className="player-statistics__heading">
                    <h2 id="statistics-title">
                      Estadísticas
                    </h2>

                    <div
                        className="statistics-tabs"
                        aria-label="Categorías de estadísticas"
                    >
                      <button
                          className="statistics-tab statistics-tab--active"
                          type="button"
                      >
                        Resumen
                      </button>

                      <button
                          className="statistics-tab"
                          type="button"
                          disabled
                          title="Disponible próximamente"
                      >
                        Ataque
                      </button>

                      <button
                          className="statistics-tab"
                          type="button"
                          disabled
                          title="Disponible próximamente"
                      >
                        Defensa
                      </button>

                      <button
                          className="statistics-tab"
                          type="button"
                          disabled
                          title="Disponible próximamente"
                      >
                        Pases
                      </button>
                    </div>
                  </div>

                  <dl className="statistics-grid">
                    {STATISTICS.map(([key, label, icon]) => (
                        <div
                            className="statistic-card"
                            key={key}
                        >
                          <div className="statistic-card__top">
                    <span className="statistic-card__icon">
                      <DetailIcon type={icon} />
                    </span>

                            <dt>
                              {label}
                            </dt>
                          </div>

                          <dd>
                            {formatNumber(statistics[key])}
                          </dd>

                          <div
                              className="statistic-card__accent"
                              aria-hidden="true"
                          />
                        </div>
                    ))}
                  </dl>
                </section>
            )
            : (
                <StatusMessage type="info">
                  Este jugador no tiene estadísticas disponibles.
                </StatusMessage>
            )}
      </section>
  )
}