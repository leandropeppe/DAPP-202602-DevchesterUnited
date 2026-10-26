import { useCallback, useEffect, useState } from 'react'
import StatusMessage from '../components/StatusMessage.jsx'
import PlayerCard from '../components/PlayerCard.jsx'
import { getPlayers } from '../api/http.js'
import { errorMessage } from './formUtils.js'

const EMPTY_FILTERS = { league: '', team: '', position: '' }
const CATALOG_FILTER_NAMES = Object.keys(EMPTY_FILTERS)

const LEAGUE_OPTIONS = [
  'Premier League',
  'LaLiga',
  'Bundesliga',
  'Serie A',
  'Ligue 1'
]

const POSITION_OPTIONS = [
  'Goalkeeper',
  'Defender',
  'Midfielder',
  'Forward'
]

function readCatalogLocation () {
  const params = new URLSearchParams(window.location.search)
  const filters = Object.fromEntries(
    CATALOG_FILTER_NAMES.map((name) => [name, params.get(name) || ''])
  )
  const parsedPage = Number.parseInt(params.get('page') || '1', 10)

  return {
    filters,
    page: Number.isFinite(parsedPage) && parsedPage > 0 ? parsedPage : 1
  }
}

function catalogUrl (filters, page) {
  const params = new URLSearchParams()
  for (const name of CATALOG_FILTER_NAMES) {
    if (filters[name]) params.set(name, filters[name])
  }
  if (page > 1) params.set('page', String(page))

  const query = params.toString()
  return '/catalogo' + (query ? `?${query}` : '')
}

export default function CatalogPage ({ onNavigate = () => {} }) {
  const initialLocation = readCatalogLocation()
  const [filters, setFilters] = useState(initialLocation.filters)
  const [page, setPage] = useState(initialLocation.page)

  const [catalog, setCatalog] = useState({
    players: [],
    pagination: {
      current_page: 1,
      total_pages: 0,
      total_records: 0
    }
  })

  const [state, setState] = useState({
    status: 'idle',
    message: ''
  })

  const loadPlayers = useCallback(async (requestedPage, requestedFilters) => {
    setState({ status: 'loading', message: '' })

    try {
      const response = await getPlayers({
        filters: {
          ...requestedFilters,
          page: requestedPage,
          limit: 20
        }
      })

      setCatalog(response?.data || {
        players: [],
        pagination: {
          current_page: requestedPage,
          total_pages: 0,
          total_records: 0
        }
      })

      setState({
        status: 'success',
        message: ''
      })
    } catch (error) {
      setState({
        status: 'error',
        message: errorMessage(error)
      })
    }
  }, [])

  useEffect(() => {
    function syncFromLocation () {
      const location = readCatalogLocation()
      setFilters(location.filters)
      setPage(location.page)
      loadPlayers(location.page, location.filters)
    }

    syncFromLocation()
    window.addEventListener('popstate', syncFromLocation)
    return () => window.removeEventListener('popstate', syncFromLocation)
  }, [loadPlayers])

  function updateFilter (event) {
    setFilters({
      ...filters,
      [event.target.name]: event.target.value
    })
  }

  function submitFilters (event) {
    event.preventDefault()

    window.history.pushState({}, '', catalogUrl(filters, 1))
    setPage(1)
    loadPlayers(1, filters)
  }

  function clearFilters () {
    const nextFilters = { ...EMPTY_FILTERS }
    window.history.pushState({}, '', catalogUrl(nextFilters, 1))
    setFilters(nextFilters)
    setPage(1)
    loadPlayers(1, nextFilters)
  }

  function changePage (nextPage) {
    window.history.pushState({}, '', catalogUrl(filters, nextPage))
    setPage(nextPage)
    loadPlayers(nextPage, filters)
  }

  function openPlayer (player) {
    onNavigate(
        `/catalogo/jugadores/${encodeURIComponent(player.id)}`
    )
  }

  const pagination = catalog.pagination || {}
  const totalPages = pagination.total_pages || 0
  const totalRecords = pagination.total_records

  return (
      <section
          className="catalog-page old-trafford-fixed"
          aria-labelledby="catalog-title"
      >
        <header className="catalog-hero">
          <div
              className="catalog-hero__stadium"
              aria-hidden="true"
          />

          <div className="catalog-hero__content">
            <p className="eyebrow">
              Datos del juego
            </p>

            <h1 id="catalog-title">
              Catálogo de jugadores
            </h1>

            <p className="catalog-hero__description">
              Explorá las plantillas disponibles y encontrá jugadores por liga,
              equipo o posición.
            </p>
          </div>
        </header>

        <form
            className="card catalog-toolbar"
            onSubmit={submitFilters}
            aria-label="Filtros del catálogo"
        >
          <div className="filter-grid">
            <div className="filter-field">
              <label htmlFor="filter-league">
                Liga
              </label>

              <select
                  id="filter-league"
                  name="league"
                  value={filters.league}
                  onChange={updateFilter}
              >
                <option value="">
                  Todas
                </option>

                {LEAGUE_OPTIONS.map((league) => (
                    <option
                        key={league}
                        value={league}
                    >
                      {league}
                    </option>
                ))}
              </select>
            </div>

            <div className="filter-field">
              <label htmlFor="filter-team">
                Equipo
              </label>

              <input
                  id="filter-team"
                  name="team"
                  value={filters.team}
                  onChange={updateFilter}
                  placeholder="Ej. Madrid"
              />
            </div>

            <div className="filter-field">
              <label htmlFor="filter-position">
                Posición
              </label>

              <select
                  id="filter-position"
                  name="position"
                  value={filters.position}
                  onChange={updateFilter}
              >
                <option value="">
                  Todas
                </option>

                {POSITION_OPTIONS.map((position) => (
                    <option
                        key={position}
                        value={position}
                    >
                      {position}
                    </option>
                ))}
              </select>
            </div>

            <div className="filter-actions">
              <button
                  className="button button--primary"
                  type="submit"
                  disabled={state.status === 'loading'}
              >
                Aplicar filtros
              </button>

              <button
                  className="button button--secondary"
                  type="button"
                  onClick={clearFilters}
                  disabled={state.status === 'loading'}
              >
                Limpiar
              </button>
            </div>
          </div>
        </form>

        {state.status === 'loading'
            ? (
                <StatusMessage type="info">
                  Cargando jugadores…
                </StatusMessage>
            )
            : null}

        {state.status === 'error'
            ? (
                <StatusMessage type="error">
                  {state.message}
                </StatusMessage>
            )
            : null}

        {state.status === 'success' && catalog.players.length === 0
            ? (
                <output className="empty-state">
                  <strong>
                    No hay jugadores que coincidan con los filtros.
                  </strong>

                  <span className="muted">
              Probá con otros términos de búsqueda.
            </span>
                </output>
            )
            : null}

        {catalog.players.length > 0
            ? (
                <>
                  <div className="catalog-summary">
                    <h2>
                      Resultados
                    </h2>

                    {totalRecords !== undefined
                        ? (
                            <span>
                    {totalRecords} jugadores encontrados
                  </span>
                        )
                        : null}
                  </div>

                  <div className="players-grid">
                    {catalog.players.map((player) => (
                        <PlayerCard
                            key={player.id}
                            player={player}
                            onOpen={openPlayer}
                        />
                    ))}
                  </div>
                </>
            )
            : null}

        <nav
            className="pagination"
            aria-label="Paginación del catálogo"
        >
          <button
              className="button button--secondary"
              type="button"
              onClick={() => changePage(page - 1)}
              disabled={
                  page <= 1 ||
                  state.status === 'loading'
              }
          >
            Anterior
          </button>

          <span
              className="pagination__label"
              aria-live="polite"
          >
          Página {pagination.current_page || page} de {totalPages}
        </span>

          <button
              className="button button--secondary"
              type="button"
              onClick={() => changePage(page + 1)}
              disabled={
                  totalPages === 0 ||
                  page >= totalPages ||
                  state.status === 'loading'
              }
          >
            Siguiente
          </button>
        </nav>
      </section>
  )
}
