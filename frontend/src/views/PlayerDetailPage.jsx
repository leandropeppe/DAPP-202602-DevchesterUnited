import { useEffect, useState } from 'react'
import PlayerCard, { getCountryCode } from '../components/PlayerCard.jsx'
import StatusMessage from '../components/StatusMessage.jsx'
import { getPlayerDetail } from '../api/http.js'
import { errorMessage } from './formUtils.js'

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
  ataque: [['goal', 'Goles'], ['assistTotal', 'Asistencias'], ['shotsPerGame', 'Tiros por partido'], ['keyPassPerGame', 'Pases clave'], ['dribbleWonPerGame', 'Regates'], ['foulGivenPerGame', 'Faltas recibidas'], ['offsideGivenPerGame', 'Fuera de juego'], ['dispossessedPerGame', 'Pérdidas'], ['turnoverPerGame', 'Turnovers']],
  defensa: [['tacklePerGame', 'Tackles'], ['interceptionPerGame', 'Intercepciones'], ['foulsPerGame', 'Faltas'], ['offsideWonPerGame', 'Fuera de juego ganados'], ['clearancePerGame', 'Despejes'], ['wasDribbledPerGame', 'Regateado'], ['outfielderBlockPerGame', 'Bloqueos'], ['goalOwn', 'Goles en propia']],
  pases: [['totalPassesPerGame', 'Pases por partido'], ['passSuccess', 'Precisión de pases'], ['accurateCrossesPerGame', 'Centros precisos'], ['accurateLongPassPerGame', 'Pases largos'], ['accurateThroughBallPerGame', 'Pases filtrados']]
}

const ALIASES = {
  minsPlayed: ['minutosJugados', 'minutes_played'], goal: ['goles', 'goals'], assistTotal: ['asistencias', 'assists'],
  shotsPerGame: ['tirosPorPartido', 'shots'], keyPassPerGame: ['pasesClavePorPartido', 'key_passes'],
  dribbleWonPerGame: ['regatesGanadosPorPartido', 'dribbles'], tacklePerGame: ['tacklesPorPartido', 'tackles'],
  rating: ['valoracion', 'rating'], yellowCard: ['tarjetasAmarillas', 'yellow_cards'], redCard: ['tarjetasRojas', 'red_cards']
}

function displayValue (result) {
  if (result === null || result === undefined || result === '') return 'Sin información'
  return typeof result === 'number' ? result.toLocaleString('es-AR', { maximumFractionDigits: 2 }) : result
}

function statisticValue (statistics, family, key) {
  const sources = [statistics?.[family], statistics]
  for (const source of sources) {
    for (const name of [key, ...(ALIASES[key] || [])]) {
      if (source?.[name] !== null && source?.[name] !== undefined && source?.[name] !== '') return displayValue(source[name])
    }
  }
  return displayValue(undefined)
}

function DetailIcon ({ type }) {
  if (type === 'yellow-card' || type === 'red-card') return <span className={`stat-icon-card stat-icon-card--${type === 'yellow-card' ? 'yellow' : 'red'}`} aria-hidden="true" />
  const icons = { clock: '◷', ball: '●', assist: '↗', target: '◎', pass: '⌁', dribble: '↝', shield: '◇', star: '☆' }
  return <span aria-hidden="true">{icons[type]}</span>
}

export default function PlayerDetailPage ({ playerId, onNavigate }) {
  const [state, setState] = useState({ status: 'loading', message: '' })
  const [player, setPlayer] = useState(null)
  const [activeTab, setActiveTab] = useState('resumen')

  useEffect(() => {
    let active = true
    async function load () {
      setState({ status: 'loading', message: '' })
      try {
        const response = await getPlayerDetail(playerId)
        if (!active) return
        setPlayer(response?.data || null)
        setState({ status: 'success', message: '' })
      } catch (error) {
        if (active) setState({ status: 'error', message: errorMessage(error) })
      }
    }
    void load()
    return () => { active = false }
  }, [playerId])

  function goBack (event) {
    event.preventDefault()
    onNavigate('/catalogo')
  }

  if (state.status === 'loading') return <StatusMessage type="info">Cargando detalle del jugador…</StatusMessage>
  if (state.status === 'error' || !player) return <section className="player-detail"><h1>No se pudo cargar el jugador</h1><StatusMessage type="error">{state.message || 'Jugador no encontrado.'}</StatusMessage><a className="button button--secondary player-detail__back" href="/catalogo" onClick={goBack}>Volver al catálogo</a></section>

  const statistics = player.statistics
  const familyKey = { ataque: 'ofensivas', defensa: 'defensivas', pases: 'pases' }[activeTab]
  const family = statistics?.[familyKey] || statistics?.[activeTab] || statistics || {}
  const countryCode = getCountryCode(player.nationality)

  return <section className="player-detail" aria-labelledby="player-detail-title">
    <div className="player-detail__hero">
      <div className="player-detail__stadium" aria-hidden="true" />
      <a className="player-detail__back-link" href="/catalogo" onClick={goBack}>← Volver al catálogo</a>
      <div className="player-detail__heading"><p className="eyebrow">Detalle del jugador</p><h1 id="player-detail-title">{displayValue(player.name)}</h1><p>{displayValue(player.team)} · {displayValue(player.league)}</p></div>
      <div className="player-detail__hero-layout"><div className="player-detail__player-card"><PlayerCard player={player} interactive={false} /></div><div className="player-detail__overview">
        <div className="player-detail__identity-grid">
          <div className="detail-info-card"><span className="detail-info-card__icon">◇</span><div><span className="player-detail__label">Posición</span><strong>{displayValue(player.position)}</strong></div></div>
          <div className="detail-info-card"><span className="detail-info-card__icon">{countryCode ? <img src={`https://flagcdn.com/${countryCode.toLowerCase()}.svg`} alt="" className="detail-info-card__flag" /> : <span aria-hidden="true">●</span>}</span><div><span className="player-detail__label">Nacionalidad</span><strong>{displayValue(player.nationality)}</strong></div></div>
          <div className="detail-info-card detail-info-card--valuation"><span className="detail-info-card__icon">◉</span><div><span className="player-detail__label">Valorización Devchester</span><strong>-</strong></div></div>
        </div>
        {statistics ? <div className="player-detail__highlights"><div className="highlight-stat"><span>Minutos jugados</span><strong>{statisticValue(statistics, 'generales', 'minsPlayed')}</strong></div><div className="highlight-stat"><span>Goles</span><strong>{statisticValue(statistics, 'ofensivas', 'goal')}</strong></div><div className="highlight-stat"><span>Valoración</span><strong>{statisticValue(statistics, 'generales', 'rating')}</strong></div></div> : null}
      </div></div>
    </div>
    {statistics ? <section className="player-statistics" aria-labelledby="statistics-title"><div className="player-statistics__heading"><h2 id="statistics-title">Estadísticas</h2><div className="statistics-tabs" role="tablist" aria-label="Categorías de estadísticas">{['resumen', ...Object.keys(FAMILIES)].map(tab => <button key={tab} className={'statistics-tab' + (activeTab === tab ? ' statistics-tab--active' : '')} type="button" role="tab" aria-selected={activeTab === tab} onClick={() => setActiveTab(tab)}>{tab === 'resumen' ? 'Resumen' : tab === 'ataque' ? 'Ataque' : tab === 'defensa' ? 'Defensa' : 'Pases'}</button>)}</div></div><dl className="statistics-grid">{activeTab === 'resumen' ? SUMMARY.map(([familyName, key, label, icon]) => <div className="statistic-card" key={key}><div className="statistic-card__top"><span className="statistic-card__icon"><DetailIcon type={icon} /></span><dt>{label}</dt></div><dd>{statisticValue(statistics, familyName, key)}</dd><div className="statistic-card__accent" aria-hidden="true" /></div>) : FAMILIES[activeTab].map(([key, label]) => <div className="statistic-card" key={key}><dt>{label}</dt><dd>{statisticValue(family, familyKey, key)}</dd><div className="statistic-card__accent" aria-hidden="true" /></div>)}</dl></section> : <StatusMessage type="info">Este jugador no tiene estadísticas disponibles.</StatusMessage>}
  </section>
}
