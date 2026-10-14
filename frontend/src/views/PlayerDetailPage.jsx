import { useEffect, useState } from 'react'
import PlayerCard from '../components/PlayerCard.jsx'
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
  Austria: 'AT',
  Belgium: 'BE',
  Brazil: 'BR',
  Cameroon: 'CM',
  Canada: 'CA',
  Chile: 'CL',
  Colombia: 'CO',
  Croatia: 'HR',
  Czechia: 'CZ',
  'Czech Republic': 'CZ',
  Denmark: 'DK',
  Ecuador: 'EC',
  Egypt: 'EG',
  England: 'GB',
  Finland: 'FI',
  France: 'FR',
  Germany: 'DE',
  Ghana: 'GH',
  Greece: 'GR',
  Hungary: 'HU',
  Iceland: 'IS',
  Ireland: 'IE',
  Italy: 'IT',
  'Ivory Coast': 'CI',
  Japan: 'JP',
  Mexico: 'MX',
  Morocco: 'MA',
  Netherlands: 'NL',
  Nigeria: 'NG',
  Norway: 'NO',
  Paraguay: 'PY',
  Peru: 'PE',
  Poland: 'PL',
  Portugal: 'PT',
  Romania: 'RO',
  Scotland: 'GB',
  Senegal: 'SN',
  Serbia: 'RS',
  Slovakia: 'SK',
  Slovenia: 'SI',
  'South Korea': 'KR',
  Spain: 'ES',
  Sweden: 'SE',
  Switzerland: 'CH',
  Tunisia: 'TN',
  Turkey: 'TR',
  Ukraine: 'UA',
  'United States': 'US',
  Uruguay: 'UY',
  Venezuela: 'VE',
  Wales: 'GB'
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

    void loadPlayer()

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
              {displayValue(player.name)}
            </h1>

            <p>
              {displayValue(player.team)}
              {' · '}
              {displayValue(player.league)}
            </p>
          </div>

          <div className="player-detail__hero-layout">
            <div className="player-detail__player-card">
              <PlayerCard
                  player={player}
                  interactive={false}
              />
            </div>

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
