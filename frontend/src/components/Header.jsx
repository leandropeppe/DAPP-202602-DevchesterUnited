export default function Header ({ route, session, onNavigate }) {
  const items = session.jwt
    ? [['/catalogo', 'Catálogo']]
    : [['/catalogo', 'Catálogo'], ['/login', 'Iniciar sesión'], ['/registro', 'Registrarse']]

  function go (event, path) {
    event.preventDefault()
    onNavigate(path)
  }

  return (
    <header className="site-header">
      <div className="site-header__inner">
        <a className="brand" href="/catalogo" onClick={(event) => go(event, '/catalogo')}>
          <img
              className="brand__logo"
              src="/images/devchester-logo-icon.png"
              alt=""
              aria-hidden="true"
          />
          <span className="brand__wordmark">
            <strong>
              <span className="brand__dev">Dev</span>chester
            </strong>
            <small>UNITED</small>
          </span>
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
            <button
              className="nav-action"
              type="button"
              onClick={() => {
                session.logout()
                onNavigate('/login')
              }}
            >
              Cerrar sesión
            </button>
          ) : null}
        </nav>
      </div>
    </header>
  )
}
