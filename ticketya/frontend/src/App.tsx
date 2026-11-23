import { useEffect, useState } from 'react'
import { apiRequest, setActiveToken, setUnauthorizedHandler } from './api/client'
import { CustomerApp } from './customer/CustomerApp'
import { demoTokens, decodeJwt, tokenValidForRole, type DemoRole } from './demo/tokens'
import { NotificationsArea } from './notifications/NotificationsArea'
import OperationsArea from './operations/OperationsArea'

type Page = 'events' | 'notifications' | 'operations'

const roleLabels: Record<DemoRole, string> = {
  CLIENT: 'Cliente',
  ORGANIZER: 'Organizador',
  ADMIN: 'Administrador',
}

export default function App() {
  const [role, setRole] = useState<DemoRole | null>(null)
  const [page, setPage] = useState<Page>('events')
  const [message, setMessage] = useState('')
  const [sessionVersion, setSessionVersion] = useState(0)
  const claims = role ? decodeJwt(demoTokens[role]) : null

  useEffect(() => {
    setUnauthorizedHandler(() => {
      setRole(null)
      setPage('events')
      setSessionVersion((version) => version + 1)
      setMessage('La sesión de prueba venció o fue rechazada. Renová los JWT de prueba.')
    })
    return () => setUnauthorizedHandler(null)
  }, [])

  function chooseRole(nextRole: DemoRole | null) {
    const nextToken = nextRole ? demoTokens[nextRole] : null
    if (nextRole && !tokenValidForRole(nextToken ?? '', nextRole)) {
      setMessage(`No hay un JWT vigente para ${roleLabels[nextRole]}. Ejecutá npm run tokens:refresh y recargá la página.`)
      return
    }
    setActiveToken(nextToken)
    setRole(nextRole)
    setPage(nextRole === 'ADMIN' || nextRole === 'ORGANIZER' ? 'operations' : 'events')
    setSessionVersion((version) => version + 1)
    setMessage('')
  }

  const identityKey = `${role ?? 'anonymous'}:${claims?.sub ?? ''}:${sessionVersion}`

  return (
    <div className="app-shell">
      <aside className="sidebar">
        <div className="brand"><span className="brand-mark">T</span><div><strong>TicketYa</strong><small>Demo de escritorio</small></div></div>
        <div className="sidebar-section">Navegación</div>
        <nav aria-label="Navegación principal">
          <button className={page === 'events' ? 'active' : ''} onClick={() => setPage('events')}>Eventos y entradas</button>
          {role === 'CLIENT' && <button className={page === 'notifications' ? 'active' : ''} onClick={() => setPage('notifications')}>Email de contacto</button>}
          {(role === 'ORGANIZER' || role === 'ADMIN') && <button className={page === 'operations' ? 'active' : ''} onClick={() => setPage('operations')}>Gestión de eventos</button>}
        </nav>
        <div className="sidebar-note">Las selecciones no reservan cupo. La disponibilidad se confirma al comprar.</div>
      </aside>
      <div className="main-column">
        <header className="topbar">
          <div><span className="eyebrow">ENTORNO DE DEMOSTRACIÓN</span><h1>{page === 'operations' ? 'Gestión de eventos' : page === 'notifications' ? 'Email de contacto' : 'Encontrá tu próximo evento'}</h1></div>
          <div className="session-controls">
            {role && <span className="session-indicator">{roleLabels[role]} · {claims?.sub.slice(0, 8)}…</span>}
            <label htmlFor="demo-profile">Perfil</label>
            <select id="demo-profile" value={role ?? ''} onChange={(event) => chooseRole(event.target.value as DemoRole || null)}>
              <option value="">Sin sesión</option>
              <option value="CLIENT">Cliente</option>
              <option value="ORGANIZER">Organizador</option>
              <option value="ADMIN">Administrador</option>
            </select>
            {role && <button type="button" className="button-quiet" onClick={() => chooseRole(null)}>Salir</button>}
          </div>
        </header>
        <main>
          {message && <div className="alert" role="alert">{message}</div>}
          {role && claims && <p className="session-expiry">Token de prueba válido hasta {new Date(claims.exp * 1000).toLocaleString('es-AR')}.</p>}
          {page === 'events' && <CustomerApp key={identityKey} api={apiRequest} profileKey={role === 'CLIENT' ? claims?.sub ?? '' : ''} />}
          {page === 'notifications' && role === 'CLIENT' && <NotificationsArea key={identityKey} api={apiRequest} />}
          {page === 'operations' && (role === 'ORGANIZER' || role === 'ADMIN') && <OperationsArea key={identityKey} role={role} userId={claims?.sub ?? ''} />}
        </main>
      </div>
    </div>
  )
}
