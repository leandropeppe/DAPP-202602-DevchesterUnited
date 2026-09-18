import { useEffect, useState } from 'react'
import { SessionProvider, useSession } from './session/SessionContext.jsx'
import RegistrationPage from './views/RegistrationPage.jsx'
import LoginPage from './views/LoginPage.jsx'
import CredentialsPage from './views/CredentialsPage.jsx'
import CatalogPage from './views/CatalogPage.jsx'
import AppLayout from './components/AppLayout.jsx'
import './styles/global.css'
import './styles/layout.css'
import './styles/catalog.css'

const ROUTES = ['/registro', '/login', '/credenciales', '/catalogo']
function resolveRoute (pathname) { return ROUTES.includes(pathname) ? pathname : '/login' }
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
      {route === '/credenciales' ? <CredentialsPage onNavigate={navigate} session={session} /> : null}
      {route === '/catalogo' ? <CatalogPage onNavigate={navigate} /> : null}
    </AppLayout>
  )
}
export default function App () { return <SessionProvider><Application /></SessionProvider> }
