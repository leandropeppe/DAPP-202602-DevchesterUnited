import {
  useEffect,
  useMemo,
  useState
} from 'react'

import PlayerCard, {
  getCountryCode,
  getPlayerTheme
} from '../components/PlayerCard.jsx'

import StatusMessage
  from '../components/StatusMessage.jsx'

import { getTeamLogo }
  from '../config/teamLogos.js'
import { getPlayerStatisticIcon }
  from '../config/playerStatisticIcons.jsx'

import {
  getPlayerDetail,
  getPlayerValuationHistory,
  getMarketBalance,
  getMarketPlayer,
  getMarketPortfolio
} from '../api/http.js'

import MarketOperationPanel from '../components/MarketOperationPanel.jsx'
import { marketPlayerUuid } from '../market/marketState.js'
import { useOptionalSession } from '../session/SessionContext.jsx'

import {
  errorMessage
} from './formUtils.js'

const SUMMARY = [
  ['generales', 'minsPlayed', 'Minutos jugados', 'clock'],
  ['ofensivas', 'goal', 'Goles', 'ball'],
  ['ofensivas', 'assistTotal', 'Asistencias', 'assist'],
  ['ofensivas', 'shotsPerGame', 'Tiros', 'target'],
  ['ofensivas', 'keyPassPerGame', 'Pases clave', 'pass'],
  ['ofensivas', 'dribbleWonPerGame', 'Regates', 'dribble'],
  ['defensivas', 'tacklePerGame', 'Tackles', 'shield'],
  ['generales', 'rating', 'Valoración', 'star'],
  ['generales', 'yellowCard', 'Tarjetas amarillas', 'yellow-card'],
  ['generales', 'redCard', 'Tarjetas rojas', 'red-card']
]

const FAMILIES = {
  ataque: [
      ['goal', 'Goles'],
      ['assistTotal', 'Asistencias'],
      ['shotsPerGame', 'Tiros por partido'],
      ['keyPassPerGame', 'Pases clave'],
      ['dribbleWonPerGame', 'Regates'],
      ['foulGivenPerGame', 'Faltas recibidas'],
      ['offsideGivenPerGame', 'Fuera de juego'],
      ['dispossessedPerGame', 'Pérdidas'],
      ['turnoverPerGame', 'Turnovers']
  ],

  defensa: [
      ['tacklePerGame', 'Tackles'],
      ['interceptionPerGame', 'Intercepciones'],
      ['foulsPerGame', 'Faltas'],
      ['offsideWonPerGame', 'Fuera de juego ganados'],
      ['clearancePerGame', 'Despejes'],
      ['wasDribbledPerGame', 'Regateado'],
      ['outfielderBlockPerGame', 'Bloqueos'],
      ['goalOwn', 'Goles en propia']
  ],

  pases: [
      ['totalPassesPerGame', 'Pases por partido'],
      ['passSuccess', 'Precisión de pases'],
      ['accurateCrossesPerGame', 'Centros precisos'],
      ['accurateLongPassPerGame', 'Pases largos'],
      ['accurateThroughBallPerGame', 'Pases filtrados']
  ]
}

const ALIASES = {
  minsPlayed: [
      'minutosJugados',
      'minutes_played'
  ],

  goal: [
      'goles',
      'goals'
  ],

  assistTotal: [
      'asistencias',
      'assists'
  ],

  shotsPerGame: [
      'tirosPorPartido',
      'shots'
  ],

  keyPassPerGame: [
      'pasesClavePorPartido',
      'key_passes'
  ],

  dribbleWonPerGame: [
      'regatesGanadosPorPartido',
      'dribbles'
  ],

  tacklePerGame: [
      'tacklesPorPartido',
      'tackles'
  ],

  rating: [
      'valoracion',
      'rating'
  ],

  yellowCard: [
      'tarjetasAmarillas',
      'yellow_cards'
  ],

  redCard: [
      'tarjetasRojas',
      'red_cards'
  ]
}

function displayValue (result) {
  if (
      result === null ||
      result === undefined ||
      result === ''
  ) {
      return 'Sin información'
  }

  return typeof result === 'number'
      ? result.toLocaleString(
          'es-AR',
          {
              maximumFractionDigits: 2
          }
      )
      : result
}

function statisticValue (
  statistics,
  family,
  key
) {
  const sources = [
      statistics?.[family],
      statistics
  ]

  for (const source of sources) {
      for (
          const name
          of [key, ...(ALIASES[key] || [])]
          ) {
          if (
              source?.[name] !== null &&
              source?.[name] !== undefined &&
              source?.[name] !== ''
          ) {
              return displayValue(
                  source[name]
              )
          }
      }
  }

  return displayValue(undefined)
}

function tabLabel (tab) {
  if (tab === 'resumen') {
      return 'Resumen'
  }

  if (tab === 'ataque') {
      return 'Ataque'
  }

  if (tab === 'defensa') {
      return 'Defensa'
  }

  return 'Pases'
}

function statisticsFamily (statistics, familyKey, activeTab) {
  return statistics?.[familyKey] || statistics?.[activeTab] || statistics || {}
}

function DetailIcon ({ statisticKey }) {
  const Icon = getPlayerStatisticIcon(statisticKey)
  const cardColor = statisticKey === 'yellowCard'
      ? 'yellow'
      : statisticKey === 'redCard'
          ? 'red'
          : null

  return (
      <Icon
          className={cardColor ? `stat-icon-card stat-icon-card--${cardColor}` : undefined}
          size={17}
          strokeWidth={2.2}
          fill={cardColor ? 'currentColor' : undefined}
          aria-hidden="true"
      />
  )
}

function buildSparklinePoints (
  values,
  width = 240,
  height = 118
) {
  const min =
      Math.min(...values)

  const max =
      Math.max(...values)

  const range =
      max - min || 1

  return values
      .map(
          (value, index) => {
              const x =
                  values.length === 1
                      ? width / 2
                      : (
                      index /
                      (values.length - 1)
                  ) * width

              const y =
                  height -
                  (
                      (value - min) /
                      range
                  ) * height

              return (
                  `${x.toFixed(1)},` +
                  `${y.toFixed(1)}`
              )
          }
      )
      .join(' ')
}

function formatValuation (value) {
  return Number(value).toLocaleString(
      'es-AR',
      {
          minimumFractionDigits: 2,
          maximumFractionDigits: 2
      }
  )
}

function playerCardClass (theme, flipped) {
  return `player-card-flip player-card-flip--${theme}` +
      (flipped ? ' player-card-flip--flipped' : '')
}

function catalogReturnPath () {
  return window.history.state?.catalogReturn || '/catalogo'
}

function ValuationLoading ({
                             onFlip
                         }) {
  return (
      <div className="valuation-card-back">

          <div className="valuation-card-back__header">
              <div>
        <span className="valuation-card-back__eyebrow">
          Historial
        </span>

                  <h3>
                      Evolución de cotización
                  </h3>
              </div>

              <button
                  className="valuation-card-back__close"
                  type="button"
                  onClick={onFlip}
                  aria-label="Volver a la ficha del jugador"
              >
                  ↻
              </button>
          </div>

          <div className="valuation-card-back__current">
      <span>
        Cargando cotizaciones…
      </span>
          </div>

      </div>
  )
}

function ValuationError ({
                           message,
                           onRetry,
                           onFlip
                       }) {
  return (
      <div className="valuation-card-back">

          <div className="valuation-card-back__header">
              <div>
        <span className="valuation-card-back__eyebrow">
          Historial
        </span>

                  <h3>
                      Evolución de cotización
                  </h3>
              </div>

              <button
                  className="valuation-card-back__close"
                  type="button"
                  onClick={onFlip}
                  aria-label="Volver a la ficha del jugador"
              >
                  ↻
              </button>
          </div>

          <div className="valuation-card-back__current">
      <span>
        {message}
      </span>
          </div>

          <button
              className="valuation-card-back__button"
              type="button"
              onClick={onRetry}
          >
              Reintentar
          </button>

      </div>
  )
}

function PlayerValuationBack ({
                                items,
                                total,
                                onFlip
                            }) {
  /*
   * Backend:
   * más reciente → más antiguo.
   *
   * Para dibujar el gráfico:
   * más antiguo → más reciente.
   */
  const history = useMemo(
      () => (
          [...items]
              .reverse()
              .map(
                  (item) => ({
                      ...item,
                      numericValue:
                          Number(item.value)
                  })
              )
              .filter(
                  (item) =>
                      Number.isFinite(
                          item.numericValue
                      )
              )
      ),
      [items]
  )

  if (history.length === 0) {
      return (
          <div className="valuation-card-back">

              <div className="valuation-card-back__header">

                  <div>
          <span className="valuation-card-back__eyebrow">
            Historial
          </span>

                      <h3>
                          Evolución de cotización
                      </h3>
                  </div>

                  <button
                      className="valuation-card-back__close"
                      type="button"
                      onClick={onFlip}
                      aria-label="Volver a la ficha del jugador"
                  >
                      ↻
                  </button>

              </div>

              <div className="valuation-card-back__current">
        <span>
          Todavía no hay cotizaciones históricas disponibles.
        </span>
              </div>

              <button
                  className="valuation-card-back__button"
                  type="button"
                  onClick={onFlip}
              >
                  ← Volver a la ficha
              </button>

          </div>
      )
  }

  const values =
      history.map(
          (item) =>
              item.numericValue
      )

  const initial =
      values[0]

  const current =
      values.at(-1)

  const max =
      Math.max(...values)

  const min =
      Math.min(...values)

  const average =
      values.reduce(
          (sum, value) =>
              sum + value,
          0
      ) / values.length

  const variation =
      history.length < 2 ||
      initial === 0
          ? 0
          : (
          (
              current - initial
          ) /
          initial
      ) * 100

  const points =
      buildSparklinePoints(
          values
      )

  return (
      <div className="valuation-card-back">

          <div className="valuation-card-back__header">

              <div>

        <span className="valuation-card-back__eyebrow">
          Últimas {
            Math.min(
                items.length,
                30
            )
        } cotizaciones
        </span>

                  <h3>
                      Evolución de cotización
                  </h3>

              </div>

              <button
                  className="valuation-card-back__close"
                  type="button"
                  onClick={onFlip}
                  aria-label="Volver a la ficha del jugador"
              >
                  ↻
              </button>

          </div>

          <div className="valuation-card-back__current">

      <span>
        Cotización actual
      </span>

              <strong>
                  {formatValuation(current)}
              </strong>

              {history.length > 1 && (
                  <span
                      className={
                          variation >= 0
                              ? 'valuation-card-back__variation valuation-card-back__variation--positive'
                              : 'valuation-card-back__variation valuation-card-back__variation--negative'
                      }
                  >
          {
              variation >= 0
                  ? '▲'
                  : '▼'
          }{' '}

                      {
                          Math.abs(
                              variation
                          ).toLocaleString(
                              'es-AR',
                              {
                                  maximumFractionDigits: 1
                              }
                          )
                      }

                      %
        </span>
              )}

          </div>

          <div className="valuation-card-back__chart">

              <svg
                  viewBox="0 0 240 118"
                  role="img"
                  aria-label={
                      `Evolución de las últimas ${history.length} cotizaciones`
                  }
                  preserveAspectRatio="none"
              >

                  <defs>

                      <linearGradient
                          id="valuation-area-gradient"
                          x1="0"
                          x2="0"
                          y1="0"
                          y2="1"
                      >

                          <stop
                              offset="0%"
                              stopColor="currentColor"
                              stopOpacity=".26"
                          />

                          <stop
                              offset="100%"
                              stopColor="currentColor"
                              stopOpacity="0"
                          />

                      </linearGradient>

                  </defs>

                  <polygon
                      className="valuation-card-back__area"
                      points={
                          `0,118 ${points} 240,118`
                      }
                      fill="url(#valuation-area-gradient)"
                  />

                  <polyline
                      className="valuation-card-back__line"
                      points={points}
                      fill="none"
                  />

              </svg>

          </div>

          <div className="valuation-card-back__summary">

              <div>
        <span>
          Máx.
        </span>

                  <strong>
                      {formatValuation(max)}
                  </strong>
              </div>

              <div>
        <span>
          Mín.
        </span>

                  <strong>
                      {formatValuation(min)}
                  </strong>
              </div>

              <div>
        <span>
          Prom.
        </span>

                  <strong>
                      {formatValuation(average)}
                  </strong>
              </div>

          </div>

          <button
              className="valuation-card-back__button"
              type="button"
              onClick={onFlip}
          >
              ← Volver a la ficha
          </button>

          {total > history.length && (
              <span
                  className="valuation-card-back__eyebrow"
              >
        {total} cotizaciones en total
      </span>
          )}

      </div>
  )
}

function ValuationHistoryFace ({
                                 valuationHistory,
                                 loadValuationHistory,
                                 onFlip
                             }) {
  return (
      <>
          {valuationHistory.status === 'loading'
              ? <ValuationLoading onFlip={onFlip} />
              : null}

          {valuationHistory.status === 'error'
              ? <ValuationError
                  message={valuationHistory.message}
                  onRetry={loadValuationHistory}
                  onFlip={onFlip}
              />
              : null}

          {valuationHistory.status === 'success'
              ? <PlayerValuationBack
                  items={valuationHistory.items}
                  total={valuationHistory.total}
                  onFlip={onFlip}
              />
              : null}
      </>
  )
}

export default function PlayerDetailPage ({
                                            playerId,
                                            onNavigate
                                        }) {
  const session = useOptionalSession()
  const [
      state,
      setState
  ] = useState({
      status: 'loading',
      message: ''
  })

  const [
      player,
      setPlayer
  ] = useState(null)

  const [
      activeTab,
      setActiveTab
  ] = useState('resumen')

  const [
      cardFlipped,
      setCardFlipped
  ] = useState(false)

  const [
      valuationHistory,
      setValuationHistory
  ] = useState({
      status: 'idle',
      items: [],
      total: 0,
      message: ''
  })

  const [marketData, setMarketData] = useState({
      status: 'idle',
      player: null,
      balance: null,
      holdings: 0,
      message: ''
  })

  async function loadMarketData () {
      if (!session?.jwt || session.isJwtExpired) {
          setMarketData((current) => ({ ...current, status: 'auth-required' }))
          return
      }

      setMarketData((current) => ({ ...current, status: 'loading', message: '' }))
      const marketId = marketPlayerUuid(playerId)
      const [balanceResult, playerResult, portfolioResult] = await Promise.allSettled([
          getMarketBalance(session.jwt),
          getMarketPlayer(marketId, session.jwt),
          getMarketPortfolio(session.jwt)
      ])
      const marketPlayer = playerResult.status === 'fulfilled'
          ? playerResult.value?.data ?? null
          : null
      const positions = portfolioResult.status === 'fulfilled' && Array.isArray(portfolioResult.value?.data?.open_positions)
          ? portfolioResult.value.data.open_positions
          : []
      const position = positions.find((candidate) => marketPlayerUuid(candidate.player_id) === marketId)
      const failed = [balanceResult, playerResult, portfolioResult].filter((result) => result.status === 'rejected')
      setMarketData({
          status: failed.length === 3 ? 'error' : 'success',
          player: marketPlayer,
          balance: balanceResult.status === 'fulfilled' ? balanceResult.value?.data?.balance ?? null : null,
          holdings: position?.quantity ?? 0,
          message: failed[0]?.reason ? errorMessage(failed[0].reason) : ''
      })
  }

  useEffect(() => {
      let active = true

      async function load () {
          setState({
              status: 'loading',
              message: ''
          })

          try {
              const response =
                  await getPlayerDetail(
                      playerId
                  )

              if (!active) {
                  return
              }

              setPlayer(
                  response?.data || null
              )

              setState({
                  status: 'success',
                  message: ''
              })
          } catch (error) {
              if (active) {
                  setState({
                      status: 'error',
                      message:
                          errorMessage(error)
                  })
              }
          }
      }

      setCardFlipped(false)

      setValuationHistory({
          status: 'idle',
          items: [],
          total: 0,
          message: ''
      })

      void loadMarketData()

      void load()

      return () => {
          active = false
      }
  }, [playerId, session?.jwt, session?.isJwtExpired])

  async function loadValuationHistory () {
      setValuationHistory(
          (current) => ({
              ...current,
              status: 'loading',
              message: ''
          })
      )

      try {
          const response =
              await getPlayerValuationHistory(
                  playerId,
                  {
                      page: 1,
                      limit: 30
                  }
              )

          const data =
              response?.data

          setValuationHistory({
              status: 'success',
              items:
                  Array.isArray(data?.items)
                      ? data.items
                      : [],
              total:
                  Number(data?.total) || 0,
              message: ''
          })
      } catch (error) {
          setValuationHistory({
              status: 'error',
              items: [],
              total: 0,
              message:
                  errorMessage(error)
          })
      }
  }

  function openValuationHistory () {
      setCardFlipped(true)

      if (
          valuationHistory.status ===
          'idle'
      ) {
          void loadValuationHistory()
      }
  }

  function goBack (event) {
      event.preventDefault()

      onNavigate(catalogReturnPath())
  }

  const catalogReturn = catalogReturnPath()

  if (
      state.status ===
      'loading'
  ) {
      return (
          <StatusMessage type="info">
              Cargando detalle del jugador…
          </StatusMessage>
      )
  }

  if (
      state.status === 'error' ||
      !player
  ) {
      return (
          <section className="player-detail old-trafford-fixed">

              <h1>
                  No se pudo cargar el jugador
              </h1>

              <StatusMessage type="error">
                  {
                      state.message ||
                      'Jugador no encontrado.'
                  }
              </StatusMessage>

              <a
                  className="button button--secondary player-detail__back"
                  href={catalogReturn}
                  onClick={goBack}
              >
                  Volver al catálogo
              </a>

          </section>
      )
  }

  const statistics =
      player.statistics

  const familyKey = {
      ataque: 'ofensivas',
      defensa: 'defensivas',
      pases: 'pases'
  }[activeTab]

  const family = statisticsFamily(statistics, familyKey, activeTab)

  const countryCode =
      getCountryCode(
          player.nationality
      )

  const playerTheme =
      getPlayerTheme(player)

  const teamLogo =
      getTeamLogo(
          player.league,
          player.team
      )

  return (
      <section
          className="player-detail old-trafford-fixed"
          aria-labelledby="player-detail-title"
      >

          <div className="player-detail__hero">

              <div
                  className="player-detail__stadium"
                  aria-hidden="true"
              />

              <a
                  className="player-detail__back-link"
                  href={catalogReturn}
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

                      <div
                                      className={playerCardClass(
                                          playerTheme,
                                          cardFlipped
                                      )}
                      >

                          <div className="player-card-flip__inner">

                              <div className="player-card-flip__face player-card-flip__front">

                                  <PlayerCard
                                      player={player}
                                      interactive={false}
                                  />

                                  <button
                                      className="player-card-flip__trigger"
                                      type="button"
                                      onClick={
                                          openValuationHistory
                                      }
                                      aria-label="Ver evolución de cotización"
                                  >
                  <span aria-hidden="true">
                    ↻
                  </span>

                                      {' '}Ver evolución
                                  </button>

                              </div>

                              <div className="player-card-flip__face player-card-flip__back">

                                  <ValuationHistoryFace
                                      valuationHistory={valuationHistory}
                                      loadValuationHistory={loadValuationHistory}
                                      onFlip={() => setCardFlipped(false)}
                                  />

                              </div>

                          </div>

                      </div>

                  </div>

                  <div className="player-detail__overview">

                      <div className="player-detail__identity-grid">

                          <div className="detail-info-card">

                <span className="detail-info-card__icon">
                  {
                      teamLogo
                          ? (
                              <img
                                  src={teamLogo}
                                  alt=""
                                  className="detail-info-card__team-logo"
                              />
                          )
                          : (
                              <span aria-hidden="true">
                              ◇
                            </span>
                          )
                  }
                </span>

                              <div>
                  <span className="player-detail__label">
                    Posición
                  </span>

                                  <strong>
                                      {displayValue(
                                          player.position
                                      )}
                                  </strong>
                              </div>

                          </div>

                          <div className="detail-info-card">

              <span className="detail-info-card__icon">

                {
                    countryCode
                        ? (
                            <img
                                src={
                                    `https://flagcdn.com/${countryCode.toLowerCase()}.svg`
                                }
                                alt=""
                                className="detail-info-card__flag"
                            />
                        )
                        : (
                            <span
                                aria-hidden="true"
                            >
                        ●
                      </span>
                        )
                }

              </span>

                              <div>

                <span className="player-detail__label">
                  Nacionalidad
                </span>

                                  <strong>
                                      {displayValue(
                                          player.nationality
                                      )}
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
                                      {
                                          player.valuation ===
                                          null ||
                                          player.valuation ===
                                          undefined
                                              ? '-'
                                              : Number(
                                                  player.valuation
                                              ).toLocaleString(
                                                  'es-AR',
                                                  {
                                                      maximumFractionDigits: 2
                                                  }
                                              )
                                      }
                                  </strong>

                              </div>

                          </div>

                      </div>

                      {
                          statistics
                              ? (
                                  <div className="player-detail__highlights">

                                      <div className="highlight-stat">

                    <span>
                      Minutos jugados
                    </span>

                                          <strong>
                                              {
                                                  statisticValue(
                                                      statistics,
                                                      'generales',
                                                      'minsPlayed'
                                                  )
                                              }
                                          </strong>

                                      </div>

                                      <div className="highlight-stat">

                    <span>
                      Goles
                    </span>

                                          <strong>
                                              {
                                                  statisticValue(
                                                      statistics,
                                                      'ofensivas',
                                                      'goal'
                                                  )
                                              }
                                          </strong>

                                      </div>

                                      <div className="highlight-stat">

                    <span>
                      Valoración
                    </span>

                                          <strong>
                                              {
                                                  statisticValue(
                                                      statistics,
                                                      'generales',
                                                      'rating'
                                                  )
                                              }
                                          </strong>

                                      </div>

                                  </div>
                              )
                              : null
                      }

                      <div className="player-detail__purchase">
                          {marketData.status === 'auth-required'
                              ? <p className="market-operation-panel__warning">Iniciá sesión para comprar tokens.</p>
                              : null}
                          {marketData.status === 'loading'
                              ? <p className="market-operation-panel__warning">Cargando datos de compra…</p>
                              : null}
                          {marketData.status === 'error'
                              ? <p className="market-operation-panel__warning">No se pudo cargar la información de compra. La información del jugador sigue disponible.</p>
                              : null}
                          {marketData.status === 'success' && marketData.player
                              ? <MarketOperationPanel
                                  player={marketData.player}
                                  type="PURCHASE"
                                  jwt={session?.jwt}
                                  embedded
                                  currentHoldings={marketData.holdings}
                                  availableBalance={marketData.balance}
                                  onConfirmed={() => { void loadMarketData() }}
                              />
                              : null}
                          {marketData.status === 'success' && !marketData.player
                              ? <>
                                  <h2>Comprar tokens</h2>
                                  <p className="market-operation-panel__warning">Este jugador no tiene una cotización operable.</p>
                              </>
                              : null}
                      </div>

                  </div>

              </div>

          </div>

          {
              statistics
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
                                  role="tablist"
                                  aria-label="Categorías de estadísticas"
                              >
                                  {
                                      [
                                          'resumen',
                                          ...Object.keys(
                                              FAMILIES
                                          )
                                      ].map(
                                          (tab) => (
                                              <button
                                                  key={tab}
                                                  className={
                                                      'statistics-tab' +
                                                      (
                                                          activeTab ===
                                                          tab
                                                              ? ' statistics-tab--active'
                                                              : ''
                                                      )
                                                  }
                                                  type="button"
                                                  role="tab"
                                                  aria-selected={
                                                      activeTab ===
                                                      tab
                                                  }
                                                  onClick={() =>
                                                      setActiveTab(
                                                          tab
                                                      )
                                                  }
                                              >
                                                  {tabLabel(tab)}
                                              </button>
                                          )
                                      )
                                  }
                              </div>

                          </div>

                          <dl className="statistics-grid">

                              {
                                  activeTab ===
                                  'resumen'
                                      ? SUMMARY.map(
                                          (
                                              [
                                                  familyName,
                                                  key,
                                                  label
                                              ]
                                          ) => (
                                              <div
                                                  className="statistic-card"
                                                  key={key}
                                              >

                                                  <div className="statistic-card__top">

                          <span className="statistic-card__icon" data-testid="statistic-card-icon">
                            <DetailIcon
                                statisticKey={key}
                            />
                          </span>

                                                      <dt>
                                                          {label}
                                                      </dt>

                                                  </div>

                                                  <dd>
                                                      {
                                                          statisticValue(
                                                              statistics,
                                                              familyName,
                                                              key
                                                          )
                                                      }
                                                  </dd>

                                                  <div
                                                      className="statistic-card__accent"
                                                      aria-hidden="true"
                                                  />

                                              </div>
                                          )
                                      )
                                      : FAMILIES[
                                          activeTab
                                          ].map(
                                          (
                                              [key, label]
                                          ) => (
                                              <div
                                                  className="statistic-card"
                                                  key={key}
                                              >

                                                  <div className="statistic-card__top">

                                                      <span className="statistic-card__icon" data-testid="statistic-card-icon">
                                                          <DetailIcon statisticKey={key} />
                                                      </span>

                                                      <dt>
                                                          {label}
                                                      </dt>

                                                  </div>

                                                  <dd>
                                                      {
                                                          statisticValue(
                                                              family,
                                                              familyKey,
                                                              key
                                                          )
                                                      }
                                                  </dd>

                                                  <div
                                                      className="statistic-card__accent"
                                                      aria-hidden="true"
                                                  />

                                              </div>
                                          )
                                      )
                              }

                          </dl>

                      </section>
                  )
                  : (
                      <StatusMessage type="info">
                          Este jugador no tiene estadísticas disponibles.
                      </StatusMessage>
                  )
          }

      </section>
  )
}
