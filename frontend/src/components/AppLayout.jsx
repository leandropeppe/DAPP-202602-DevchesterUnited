import Header from './Header.jsx'

export default function AppLayout ({ route, session, onNavigate, children }) {
  return (
    <div className="app-shell">
      <Header route={route} session={session} onNavigate={onNavigate} />
      <main className="page-container">{children}</main>
      <footer className="site-footer">Devchester United · Catálogo de jugadores</footer>
    </div>
  )
}
