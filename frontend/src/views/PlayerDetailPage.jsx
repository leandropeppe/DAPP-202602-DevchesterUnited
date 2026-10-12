import { useEffect, useState } from 'react'
import StatusMessage from '../components/StatusMessage.jsx'
import { getPlayerDetail } from '../api/http.js'
import { errorMessage } from './formUtils.js'

const STATISTICS = [
  ['minutes_played', 'Minutos jugados'],
  ['goals', 'Goles'],
  ['assists', 'Asistencias'],
  ['shots', 'Tiros'],
  ['key_passes', 'Pases clave'],
  ['dribbles', 'Regates'],
  ['tackles', 'Tackles'],
  ['rating', 'Valoracion'],
  ['yellow_cards', 'Tarjetas amarillas'],
  ['red_cards', 'Tarjetas rojas']
]

function displayValue (value) {
  if (value === null || value === undefined || value === '') return 'Sin informacion'
  return value
}

function formatNumber (value) {
  if (typeof value !== 'number') return displayValue(value)
  return value.toLocaleString('es-AR', { maximumFractionDigits: 2 })
}

export default function PlayerDetailPage ({ playerId, onNavigate }) {
  const [state, setState] = useState({ status: 'loading', message: '' })
  const [player, setPlayer] = useState(null)

  useEffect(() => {
    let active = true

    async function loadPlayer () {
      setState({ status: 'loading', message: '' })

      try {
        const response = await getPlayerDetail(playerId)
        if (!active) return
        setPlayer(response?.data || null)
        setState({ status: 'success', message: '' })
      } catch (error) {
        if (!active) return
        setState({ status: 'error', message: errorMessage(error) })
      }
    }

    loadPlayer()
    return () => { active = false }
  }, [playerId])

  function goBack (event) {
    event.preventDefault()
    onNavigate('/catalogo')
  }

  if (state.status === 'loading') {
    return <StatusMessage type="info">Cargando detalle del jugador…</StatusMessage>
  }

  if (state.status === 'error' || !player) {
    return (
      <section className="player-detail" aria-labelledby="player-detail-error-title">
        <h1 id="player-detail-error-title">No se pudo cargar el jugador</h1>
        <StatusMessage type="error">{state.message || 'Jugador no encontrado.'}</StatusMessage>
        <a className="button button--secondary player-detail__back" href="/catalogo" onClick={goBack}>
          Volver al catalogo
        </a>
      </section>
    )
  }

  const statistics = player.statistics

  return (
    <section className="player-detail" aria-labelledby="player-detail-title">
      <a className="player-detail__back-link" href="/catalogo" onClick={goBack}>
        ← Volver al catalogo
      </a>
      <div className="page-heading">
        <p className="eyebrow">Detalle del jugador</p>
        <h1 id="player-detail-title">{player.name}</h1>
        <p>{displayValue(player.team)} · {displayValue(player.league)}</p>
      </div>

      <div className="card player-detail__summary">
        <div>
          <span className="player-detail__label">Posicion</span>
          <strong>{displayValue(player.position)}</strong>
        </div>
        <div>
          <span className="player-detail__label">Nacionalidad</span>
          <strong>{displayValue(player.nationality)}</strong>
        </div>
      </div>

      {statistics ? (
        <section aria-labelledby="statistics-title">
          <h2 id="statistics-title">Estadisticas</h2>
          <dl className="statistics-grid">
            {STATISTICS.map(([key, label]) => (
              <div className="card statistic-card" key={key}>
                <dt>{label}</dt>
                <dd>{formatNumber(statistics[key])}</dd>
              </div>
            ))}
          </dl>
        </section>
      ) : (
        <StatusMessage type="info">Este jugador no tiene estadisticas disponibles.</StatusMessage>
      )}
    </section>
  )
}
