export default function Header ({ route, session, onNavigate }) {
  const items = session.jwt
    ? [['/catalogo', 'Catálogo'], ['/credenciales', 'Credenciales']]
    : [['/catalogo', 'Catálogo'], ['/login', 'Iniciar sesión'], ['/registro', 'Registrarse']]

  function go (event, path) {
    event.preventDefault()
    onNavigate(path)
  }

  return (
    <header className="site-header">
      <div className="site-header__inner">
        <a className="brand" href="/catalogo" onClick={(event) => go(event, '/catalogo')}>
          <span className="brand__mark" aria-hidden="true">DU</span>
          <span>Devchester United</span>
        </a>
        <nav className="primary-nav" aria-label="Navegación principal">
          {items.map(([path, label]) => (
            <a
              className="primary-nav__link"
              key={path}
              href={path}
              aria-label={path === '/login' ? 'login' : undefined}
              aria-current={route === path ? 'page' : undefined}
              onClick={(event) => go(event, path)}
            >
              {label}
            </a>
          ))}
          {session.jwt ? (
            <button className="nav-action" type="button" onClick={() => { session.logout(); onNavigate('/login') }}>
              Cerrar sesión
            </button>
          ) : null}
        </nav>
      </div>
    </header>
  )
}
