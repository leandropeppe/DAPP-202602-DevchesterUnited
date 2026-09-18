import { useCallback, useEffect, useState } from 'react'
import { getPlayers } from '../api/http.js'
import { errorMessage } from './formUtils.js'
import StatusMessage from '../components/StatusMessage.jsx'

const EMPTY_FILTERS = { league: '', team: '', position: '' }
const LEAGUE_OPTIONS = ['Premier League', 'LaLiga', 'Bundesliga', 'Serie A', 'Ligue 1']
const POSITION_OPTIONS = ['Goalkeeper', 'Defender', 'Midfielder', 'Forward']
const noInfo = (value) => value === null || value === undefined || value === '' ? 'Sin información' : value

export default function CatalogPage () {
  const [filters, setFilters] = useState(EMPTY_FILTERS)
  const [page, setPage] = useState(1)
  const [catalog, setCatalog] = useState({ players: [], pagination: { current_page: 1, total_pages: 0, total_records: 0 } })
  const [state, setState] = useState({ status: 'idle', message: '' })
  const loadPlayers = useCallback(async (requestedPage, requestedFilters) => {
    setState({ status: 'loading', message: '' })
    try { const response = await getPlayers({ filters: { ...requestedFilters, page: requestedPage, limit: 20 } }); setCatalog(response?.data || { players: [], pagination: { current_page: requestedPage, total_pages: 0, total_records: 0 } }); setState({ status: 'success', message: '' }) } catch (error) { setState({ status: 'error', message: errorMessage(error) }) }
  }, [])
  useEffect(() => { loadPlayers(1, EMPTY_FILTERS) }, [loadPlayers])
  function updateFilter (event) { setFilters({ ...filters, [event.target.name]: event.target.value }) }
  function submitFilters (event) { event.preventDefault(); setPage(1); loadPlayers(1, filters) }
  function clearFilters () { setFilters(EMPTY_FILTERS); setPage(1); loadPlayers(1, EMPTY_FILTERS) }
  function changePage (nextPage) { setPage(nextPage); loadPlayers(nextPage, filters) }
  const pagination = catalog.pagination || {}; const totalPages = pagination.total_pages || 0; const totalRecords = pagination.total_records
  return (
    <section aria-labelledby="catalog-title">
      <div className="page-heading"><p className="eyebrow">Datos del juego</p><h1 id="catalog-title">Catálogo de jugadores</h1><p>Explorá las plantillas disponibles y encontrá jugadores por liga, equipo o posición.</p></div>
      <form className="card catalog-toolbar" onSubmit={submitFilters} aria-label="Filtros del catálogo">
        <div className="filter-grid">
          <div className="filter-field"><label htmlFor="filter-league">Liga</label><select id="filter-league" name="league" value={filters.league} onChange={updateFilter}><option value="">Todas</option>{LEAGUE_OPTIONS.map((league) => <option key={league} value={league}>{league}</option>)}</select></div>
          <div className="filter-field"><label htmlFor="filter-team">Equipo</label><input id="filter-team" name="team" value={filters.team} onChange={updateFilter} placeholder="Ej. Madrid" /></div>
          <div className="filter-field"><label htmlFor="filter-position">Posición</label><select id="filter-position" name="position" value={filters.position} onChange={updateFilter}><option value="">Todas</option>{POSITION_OPTIONS.map((position) => <option key={position} value={position}>{position}</option>)}</select></div>
          <div className="filter-actions"><button className="button button--primary" type="submit" disabled={state.status === 'loading'}>Aplicar filtros</button><button className="button button--secondary" type="button" onClick={clearFilters} disabled={state.status === 'loading'}>Limpiar</button></div>
        </div>
      </form>
      {state.status === 'loading' ? <StatusMessage type="info">Cargando jugadores…</StatusMessage> : null}
      {state.status === 'error' ? <StatusMessage type="error">{state.message}</StatusMessage> : null}
      {state.status === 'success' && catalog.players.length === 0 ? <div className="empty-state" role="status"><strong>No hay jugadores que coincidan con los filtros.</strong><span className="muted">Probá con otros términos de búsqueda.</span></div> : null}
      {catalog.players.length > 0 ? <>
        <div className="catalog-summary"><h2>Resultados</h2>{totalRecords !== undefined ? <span>{totalRecords} jugadores encontrados</span> : null}</div>
        <div className="players-table-wrap"><table className="players-table"><caption className="sr-only">Jugadores del catálogo</caption><thead><tr><th>Jugador</th><th>Liga</th><th>Equipo</th><th>Posición</th><th>Dorsal</th></tr></thead><tbody>{catalog.players.map((player) => <tr key={player.id}><td data-label="Jugador">{player.name}</td><td data-label="Liga">{player.league}</td><td data-label="Equipo">{player.team}</td><td data-label="Posición"><span className="sr-only">Posición: {noInfo(player.position)}</span><span aria-hidden="true">{noInfo(player.position)}</span></td><td data-label="Dorsal"><span className="sr-only">Dorsal: {noInfo(player.jersey_number)}</span><span aria-hidden="true">{noInfo(player.jersey_number)}</span></td></tr>)}</tbody></table></div>
      </> : null}
      <nav className="pagination" aria-label="Paginación del catálogo"><button className="button button--secondary" type="button" onClick={() => changePage(page - 1)} disabled={page <= 1 || state.status === 'loading'}>Anterior</button><span className="pagination__label" aria-live="polite">Página {pagination.current_page || page} de {totalPages}</span><button className="button button--secondary" type="button" onClick={() => changePage(page + 1)} disabled={totalPages === 0 || page >= totalPages || state.status === 'loading'}>Siguiente</button></nav>
    </section>
  )
}
