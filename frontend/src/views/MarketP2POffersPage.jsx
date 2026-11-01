import { useCallback, useEffect, useState } from 'react'
import { cancelMarketP2POffer, getMarketP2POffers, getMarketPortfolio, publishMarketP2POffer, purchaseMarketP2POffer } from '../api/http.js'
import MarketP2PConfirmationModal from '../components/MarketP2PConfirmationModal.jsx'
import MarketP2POfferCard from '../components/MarketP2POfferCard.jsx'
import MarketStateMessage from '../components/MarketStateMessage.jsx'
import { useSession } from '../session/SessionContext.jsx'
import { errorMessage } from './formUtils.js'

const EMPTY_RESULT = { offers: [], pagination: { current_page: 1, page_size: 20, total_pages: 0, total_records: 0 } }

function newIdempotencyKey () {
  const cryptoApi = globalThis.crypto
  if (typeof cryptoApi?.randomUUID === 'function') return cryptoApi.randomUUID()
  if (typeof cryptoApi?.getRandomValues === 'function') {
    const randomBytes = new Uint8Array(16)
    cryptoApi.getRandomValues(randomBytes)
    const randomPart = Array.from(randomBytes, (byte) => byte.toString(16).padStart(2, '0')).join('')
    return `p2p-${randomPart}`
  }
  throw new Error('No hay una API criptográfica disponible para generar una clave segura.')
}

function normalizeQuote (quote) {
  return quote ? { ...quote, quoteId: quote.quote_id, unitPrice: quote.unit_price, quoteVersion: quote.version } : null
}

function normalizeOffer (offer) {
  return { ...offer, playerId: offer?.player_id, sellerAlias: offer?.seller_alias, remainingQuantity: offer?.remaining_quantity, playerName: offer?.player_name, mine: offer?.mine === true, quote: normalizeQuote(offer?.quote) }
}

function normalizePosition (position) {
  const totalQuantity = Number(position?.total_quantity ?? position?.quantity ?? 0)
  const reservedQuantity = Number(position?.reserved_quantity ?? 0)
  const availableQuantity = Number(position?.available_quantity ?? (totalQuantity - reservedQuantity))
  return { ...position, playerId: position?.player_id, playerName: position?.player_name, totalQuantity, reservedQuantity, availableQuantity, quantity: availableQuantity, quote: position?.quote_id ? { quoteId: position.quote_id, quoteVersion: position.quote_version, unitPrice: position.unit_price } : null }
}

function isOfferListResponse (response) { return response?.data && Array.isArray(response.data.offers) && response.data.pagination }
function isPortfolioResponse (response) { return response?.data && Array.isArray(response.data.open_positions) }

export default function MarketP2POffersPage () {
  const session = useSession()
  const [filters, setFilters] = useState({ playerId: '', status: '', mine: false })
  const [page, setPage] = useState(1)
  const [result, setResult] = useState(EMPTY_RESULT)
  const [positions, setPositions] = useState([])
  const [state, setState] = useState({ status: 'loading', message: '' })
  const [operation, setOperation] = useState(null)
  const [operationState, setOperationState] = useState({ status: 'idle', message: '' })
  const [quantity, setQuantity] = useState('1')
  const [idempotencyKey, setIdempotencyKey] = useState(null)
  const [publicationPlayerId, setPublicationPlayerId] = useState(() => new URLSearchParams(window.location.search).get('publishPlayerId') || '')

  const load = useCallback(async (requestedPage, requestedFilters) => {
    if (!session.jwt || session.isJwtExpired) return
    setState({ status: 'loading', message: '' })
    try {
      const [offersResponse, portfolioResponse] = await Promise.all([
        getMarketP2POffers({ jwt: session.jwt, filters: { playerId: requestedFilters.playerId || undefined, status: requestedFilters.status || undefined, mine: requestedFilters.mine ? 'true' : undefined, page: requestedPage, limit: 20 } }),
        getMarketPortfolio(session.jwt)
      ])
      if (!isOfferListResponse(offersResponse)) throw new Error('El servidor devolvió un listado de ofertas incompleto.')
      if (!isPortfolioResponse(portfolioResponse)) throw new Error('El servidor devolvió un portfolio incompleto.')
      setResult({ offers: offersResponse.data.offers.map(normalizeOffer), pagination: offersResponse.data.pagination })
      const normalizedPositions = portfolioResponse.data.open_positions.map(normalizePosition)
      setPositions(normalizedPositions)
      setPublicationPlayerId((current) => {
        if (current && normalizedPositions.some((position) => position.playerId === current && position.quote && position.quantity > 0)) return current
        return normalizedPositions.find((position) => position.quote && position.quantity > 0)?.playerId || ''
      })
      setState({ status: 'success', message: '' })
    } catch (error) {
      setState({ status: error?.status === 503 ? 'unavailable' : 'error', message: errorMessage(error) })
    }
  }, [session.isJwtExpired, session.jwt])

  useEffect(() => { void load(page, filters) }, [filters, load, page])

  function openOperation (mode, subject, target, limit = null) {
    let key
    try {
      key = newIdempotencyKey()
    } catch (error) {
      setOperation(null)
      setIdempotencyKey(null)
      setOperationState({ status: 'idle', message: '' })
      setState({ status: 'error', message: errorMessage(error) })
      return
    }
    setOperation({ mode, subject, target, maxQuantity: limit })
    setQuantity('1')
    setIdempotencyKey(key)
    setOperationState({ status: 'idle', message: '' })
  }

  function openOfferOperation (offer) {
    if (offer.mine && ['OPEN', 'PARTIALLY_FILLED', 'PURCHASABLE', 'SUSPENDED'].includes(offer.status)) openOperation('cancel', offer.playerName || `Oferta ${offer.offer_id}`, offer)
    else if (!offer.mine && offer.status === 'PURCHASABLE') openOperation('purchase', offer.playerName || `Oferta ${offer.offer_id}`, offer, offer.remainingQuantity)
  }

  function openPublication () {
    const candidate = positions.find((position) => position.playerId === publicationPlayerId && position.quote && position.quantity > 0)
    if (candidate) openOperation('publish', candidate.playerName || `Jugador ${candidate.playerId}`, candidate, candidate.quantity)
  }

  async function executeOperation () {
    if (!operation || operationState.status === 'loading' || !idempotencyKey) return
    setOperationState({ status: 'loading', message: '' })
    try {
      if (operation.mode === 'publish') await publishMarketP2POffer({ jwt: session.jwt, idempotencyKey, body: { player_id: operation.target.playerId, quantity: Number(quantity) } })
      else if (operation.mode === 'cancel') await cancelMarketP2POffer({ jwt: session.jwt, idempotencyKey, offerId: operation.target.offer_id })
      else {
        const quote = operation.target.quote
        await purchaseMarketP2POffer({ jwt: session.jwt, idempotencyKey, offerId: operation.target.offer_id, body: { quantity: Number(quantity), quote_id: quote.quoteId, quote_version: quote.quoteVersion } })
      }
      setOperationState({ status: 'success', message: '' })
      await load(page, filters)
    } catch (error) {
      if (error?.code === 'QUOTE_CHANGED' && operation.mode === 'purchase') {
        const refreshedTarget = { ...operation.target, quote: normalizeQuote(error.currentQuote) }
        setOperation({ ...operation, target: refreshedTarget })
        setIdempotencyKey(newIdempotencyKey())
        setOperationState({ status: 'error', message: error.currentQuote ? 'La cotización cambió. Revisá la nueva cotización y confirmá nuevamente.' : 'La oferta está suspendida porque no hay una cotización válida.' })
      } else setOperationState({ status: 'error', message: errorMessage(error) })
    }
  }

  function updateFilter (event) {
    const { name, value, checked, type } = event.target
    setPage(1)
    setFilters((current) => ({ ...current, [name]: type === 'checkbox' ? checked : value }))
  }

  if (!session.jwt || session.isJwtExpired) return <section className="market-page" aria-labelledby="p2p-offers-title"><MarketStateMessage status="error" message="Iniciá sesión para operar ofertas P2P." /></section>
  if (state.status === 'loading') return <section className="market-page" aria-labelledby="p2p-offers-title"><MarketStateMessage status="loading" /></section>
  if (state.status === 'error' || state.status === 'unavailable') return <section className="market-page" aria-labelledby="p2p-offers-title"><MarketStateMessage status={state.status} message={state.message} /></section>

  const offers = result.offers || []
  const pagination = result.pagination || EMPTY_RESULT.pagination
  const publicationAvailable = positions.some((position) => position.quote && position.quantity > 0)

  return (
    <section className="market-page" aria-labelledby="p2p-offers-title">
      <div className="page-heading"><p className="eyebrow">Token Market</p><h1 id="p2p-offers-title">Ofertas P2P</h1><p>Publicá, comprá o cancelá usando la cotización oficial.</p><label className="field" htmlFor="p2p-publication-player"><span>Publicar desde mi posición</span><select id="p2p-publication-player" value={publicationPlayerId} onChange={(event) => setPublicationPlayerId(event.target.value)} disabled={!publicationAvailable}><option value="">Seleccioná un jugador</option>{positions.filter((position) => position.quote && position.quantity > 0).map((position) => <option key={position.playerId} value={position.playerId}>{position.playerName || position.playerId} ({position.quantity} tokens)</option>)}</select></label><button className="button button--primary" type="button" onClick={openPublication} disabled={!publicationAvailable || !publicationPlayerId}>Publicar oferta</button></div>
      <form className="card market-filters" onSubmit={(event) => event.preventDefault()}><div className="market-filters__controls"><label className="field" htmlFor="p2p-offer-player"><span>Jugador</span><input id="p2p-offer-player" name="playerId" value={filters.playerId} onChange={updateFilter} placeholder="UUID del jugador" /></label><label className="field" htmlFor="p2p-offer-status"><span>Estado</span><select id="p2p-offer-status" name="status" value={filters.status} onChange={updateFilter}><option value="">Todos</option><option value="PURCHASABLE">Disponibles</option><option value="SUSPENDED">Suspendidas</option><option value="FILLED">Completadas</option><option value="CANCELLED">Canceladas</option></select></label><label className="market-p2p-checkbox" htmlFor="p2p-offer-mine"><input id="p2p-offer-mine" name="mine" type="checkbox" checked={filters.mine} onChange={updateFilter} /> Solo mis ofertas</label></div></form>
      {offers.length === 0 ? <output className="empty-state">No hay ofertas P2P para estos filtros.</output> : <div className="market-p2p-offers-list">{offers.map((offer) => <MarketP2POfferCard key={offer.offer_id} offer={offer} onAction={openOfferOperation} />)}</div>}
      <nav className="pagination" aria-label="Paginación de ofertas P2P"><button className="button button--secondary" type="button" onClick={() => setPage((current) => Math.max(1, current - 1))} disabled={page <= 1}>Anterior</button><span className="pagination__label">Página {pagination.current_page || page} de {pagination.total_pages || 0}</span><button className="button button--secondary" type="button" onClick={() => setPage((current) => current + 1)} disabled={pagination.total_pages <= page}>Siguiente</button></nav>
      <MarketP2PConfirmationModal open={Boolean(operation)} mode={operation?.mode} subject={operation?.subject} quote={operation?.target?.quote} quantity={quantity} maxQuantity={operation?.maxQuantity} state={operationState.status} message={operationState.message} onQuantityChange={setQuantity} onConfirm={executeOperation} onCancel={() => setOperation(null)} />
    </section>
  )
}
