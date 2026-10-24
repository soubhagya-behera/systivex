export function AppShell({
  navItems = [],
  activeKey,
  onNavigate,
  statusSlot = null,
  children,
}) {
  return (
    <div className="app-shell">
      <header className="app-header">
        <div className="brand">
          <span className="brand__mark">Systivex</span>
          <span className="brand__sub">System Twin · read-only</span>
        </div>
        <div className="header__spacer" />
        {statusSlot}
      </header>
      <div className="app-body">
        <nav className="sidebar" aria-label="Twin sections">
          <span className="sidebar__title">Explorer</span>
          {navItems.map((item) => (
            <button
              key={item.key}
              type="button"
              className="nav-item"
              aria-current={item.key === activeKey ? 'page' : undefined}
              onClick={() => onNavigate(item.key)}
            >
              <span aria-hidden="true">{item.icon}</span>
              <span>{item.label}</span>
              {typeof item.count === 'number' ? (
                <span className="nav-item__count">{item.count}</span>
              ) : null}
            </button>
          ))}
        </nav>
        <main className="content" id="main-content">
          {children}
        </main>
      </div>
    </div>
  );
}
