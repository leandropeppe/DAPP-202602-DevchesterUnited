import { useEffect, useState } from 'react'
import { SessionProvider, useSession } from './session/SessionContext.jsx'
import RegistrationPage from './views/RegistrationPage.jsx'
import LoginPage from './views/LoginPage.jsx'
import CatalogPage from './views/CatalogPage.jsx'
import PlayerDetailPage from './views/PlayerDetailPage.jsx'
import MarketPage from './views/MarketPage.jsx'
import MarketPortfolioPage from './views/MarketPortfolioPage.jsx'
import MarketTransactionsPage from './views/MarketTransactionsPage.jsx'
import AppLayout from './components/AppLayout.jsx'
import './styles/global.css'
import './styles/layout.css'
import './styles/catalog.css'
import './styles/player-detail.css'
import './styles/market.css'

const ROUTES = new Set(['/registro', '/login', '/catalogo', '/market', '/market/portfolio', '/market/transactions'])
const PLAYER_DETAIL_ROUTE = /^\/catalogo\/jugadores\/([^/]+)$/
function resolveRoute (pathname) { return ROUTES.has(pathname) || PLAYER_DETAIL_ROUTE.test(pathname) ? pathname : '/login' }
function playerIdFromRoute (route) { return route.match(PLAYER_DETAIL_ROUTE)?.[1] || '' }
export function navigate (pathname) { window.history.pushState({}, '', pathname); window.dispatchEvent(new PopStateEvent('popstate')) }

function Application () {
  const [route, setRoute] = useState(() => resolveRoute(window.location.pathname))
  const session = useSession()
  useEffect(() => {
    const handleNavigation = () => setRoute(resolveRoute(window.location.pathname))
    window.addEventListener('popstate', handleNavigation)
    return () => window.removeEventListener('popstate', handleNavigation)
  }, [])
  return (
    <AppLayout route={route} session={session} onNavigate={navigate}>
      {route === '/registro' ? <RegistrationPage onNavigate={navigate} /> : null}
      {route === '/login' ? <LoginPage onNavigate={navigate} session={session} /> : null}
      {route === '/catalogo' ? <CatalogPage onNavigate={navigate} /> : null}
      {route === '/market' ? <MarketPage onNavigate={navigate} /> : null}
      {route === '/market/portfolio' ? <MarketPortfolioPage /> : null}
      {route === '/market/transactions' ? <MarketTransactionsPage /> : null}
      {PLAYER_DETAIL_ROUTE.test(route) ? <PlayerDetailPage playerId={playerIdFromRoute(route)} onNavigate={navigate} /> : null}
    </AppLayout>
  )
}
export default function App () { return <SessionProvider><Application /></SessionProvider> }
