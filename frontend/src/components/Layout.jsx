import { Outlet, NavLink } from 'react-router-dom'

const navItems = [
  { to: '/upload', label: 'Importar' },
  { to: '/dashboard', label: 'Dashboard' },
  { to: '/products', label: 'Productos' },
  { to: '/params', label: 'Parámetros' },
]

export default function Layout() {
  return (
    <div className="min-h-screen flex flex-col bg-slate-50">
      <header className="bg-slate-800 text-slate-100 border-b border-slate-700">
        <div className="max-w-6xl mx-auto px-4 h-14 flex items-center justify-between">
          <h1 className="text-base font-semibold tracking-tight">
            Sistema de gestión de rentabilidad
          </h1>
          <nav className="flex items-center gap-1">
            {navItems.map(item => (
              <NavLink
                key={item.to}
                to={item.to}
                className={({ isActive }) =>
                  `px-3 py-1.5 rounded text-sm transition-colors ${
                    isActive
                      ? 'bg-slate-700 text-white'
                      : 'text-slate-300 hover:text-white hover:bg-slate-700/60'
                  }`
                }
              >
                {item.label}
              </NavLink>
            ))}
          </nav>
        </div>
      </header>
      <main className="flex-1 max-w-6xl mx-auto w-full px-4 py-7">
        <Outlet />
      </main>
    </div>
  )
}
