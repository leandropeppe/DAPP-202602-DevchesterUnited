import '../styles/playerCard.css'
import { getCountryCode, getPlayerTheme } from './PlayerCard.jsx'
import { getTeamLogo } from '../config/teamLogos.js'
import QuoteStatus from './QuoteStatus.jsx'

const SILHOUETTES = {
  blue: '/images/player-silhouette-blue.png',
  red: '/images/player-silhouette-red.png',
  green: '/images/player-silhouette-green.png',
  purple: '/images/player-silhouette-purple.png',
  neutral: '/images/player-silhouette-blue.png'
}

const LEAGUE_LOGOS = {
  'Premier League': '/images/leagues/premier-league.png',
  LaLiga: '/images/leagues/la-liga.png',
  'Serie A': '/images/leagues/serie-a.png',
  Bundesliga: '/images/leagues/bundesliga.png',
  'Ligue 1': '/images/leagues/ligue-1.png'
}

function displayValue (value, formatter) {
  return value === null || value === undefined || value === ''
    ? 'Sin información'
    : formatter(value)
}

function amount (value) {
  return displayValue(value, item => Number(item).toLocaleString('es-AR', { minimumFractionDigits: 2, maximumFractionDigits: 2 }))
}

function positionLabel (position) {
  return position?.position || 'Posición no disponible'
}

function positionAbbreviation (position) {
  const labels = { Goalkeeper: 'PO', Defender: 'DF', Midfielder: 'MC', Forward: 'DL' }
  return labels[position?.position] || '—'
}

function pnlClass (value) {
  const number = Number(value)
  if (!Number.isFinite(number) || number === 0) return 'market-portfolio-value--neutral'
  return number > 0 ? 'market-portfolio-value--positive' : 'market-portfolio-value--negative'
}

function quantities (position) {
  const total = Number(position.total_quantity ?? position.quantity ?? 0)
  const reserved = Number(position.reserved_quantity ?? 0)
  const available = Number(position.available_quantity ?? (total - reserved))
  return { total, reserved, available }
}

function canPublish (position) {
  const { available } = quantities(position)
  return position.quote_availability === 'CURRENT' &&
    position.quote_id !== null && position.quote_id !== undefined && position.quote_id !== '' &&
    position.quote_version !== null && position.quote_version !== undefined &&
    position.unit_price !== null && position.unit_price !== undefined &&
    Number.isInteger(available) && available > 0
}

export default function MarketPortfolioPlayerCard ({ position, onNavigate }) {
  const theme = getPlayerTheme(position)
  const { total, reserved, available } = quantities(position)
  const teamLogo = getTeamLogo(position.league, position.team)
  const leagueLogo = LEAGUE_LOGOS[position.league]
  const countryCode = getCountryCode(position.nationality)
  const valuation = position.valuation ?? position.rating
  const playerName = position.player_name || `Jugador ${position.player_id}`

  return (
    <article className={`market-portfolio-player-card player-card player-card--${theme}`}>
      <div className="player-card__shine" aria-hidden="true" />
      <div className="player-card__rating">
        <strong>{valuation !== null && valuation !== undefined && valuation !== '' ? Number(valuation).toLocaleString('es-AR', { maximumFractionDigits: 2 }) : '—'}</strong>
        <span>{positionAbbreviation(position)}</span>
      </div>
      <div className="player-card__portrait" aria-hidden="true">
        <img src={SILHOUETTES[theme] || SILHOUETTES.neutral} alt="" className="player-card__silhouette" />
      </div>
      <div className="player-card__content">
        <h2 className="player-card__name">{playerName}</h2>
        <div className="market-portfolio-player-card__identity">
          <span className="market-portfolio-player-card__position">{positionLabel(position)}</span>
          <span>{teamLogo ? <img src={teamLogo} alt="" /> : null}{position.team || 'Equipo no disponible'}</span>
          <span>{leagueLogo ? <img src={leagueLogo} alt="" /> : null}{position.league || 'Liga no disponible'}</span>
          {countryCode ? <span><img src={`https://flagcdn.com/${countryCode.toLowerCase()}.svg`} alt="" />{position.nationality}</span> : null}
        </div>
      </div>

      <div className="market-portfolio-player-card__details">
        <section aria-labelledby={`portfolio-tokens-${position.player_id}`}>
          <h3 id={`portfolio-tokens-${position.player_id}`}>Tokens</h3>
          <dl className="market-portfolio-player-card__facts">
            <div><dt>Total</dt><dd>{total}</dd></div>
            <div><dt>Reservados</dt><dd>{reserved}</dd></div>
            <div><dt>Disponibles</dt><dd>{available}</dd></div>
          </dl>
        </section>
        <section aria-labelledby={`portfolio-finance-${position.player_id}`}>
          <h3 id={`portfolio-finance-${position.player_id}`}>Información financiera</h3>
          <dl className="market-portfolio-player-card__facts market-portfolio-player-card__facts--finance">
            <div><dt>Costo pendiente</dt><dd>{amount(position.pending_acquisition_cost)}</dd></div>
            <div><dt>Costo promedio</dt><dd>{amount(position.weighted_average_cost)}</dd></div>
            <div><dt>Última cotización</dt><dd>{amount(position.unit_price)}</dd></div>
            <div><dt>Valor actual</dt><dd>{amount(position.current_value)}</dd></div>
            <div><dt>P/L no realizada</dt><dd className={pnlClass(position.unrealized_pnl)}>{amount(position.unrealized_pnl)}</dd></div>
          </dl>
        </section>
        <QuoteStatus status={position.quote_availability} price={position.unit_price === null ? null : amount(position.unit_price)} />
        {!canPublish(position) ? <p className="market-portfolio-player-card__unavailable">No es posible publicar una oferta con la cotización o cantidad disponible actuales.</p> : null}
        {canPublish(position)
          ? <button className="button button--primary market-portfolio-player-card__action" type="button" onClick={() => onNavigate?.(`/market/p2p/offers?publishPlayerId=${encodeURIComponent(position.player_id)}`)}>Publicar oferta P2P</button>
          : null}
      </div>
    </article>
  )
}
