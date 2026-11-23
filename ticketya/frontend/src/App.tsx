import { useEffect, useRef, useState } from 'react'
import { apiRequest, renewActiveToken, setActiveToken, setUnauthorizedHandler } from './api/client'
import { CustomerApp } from './customer/CustomerApp'
import { decodeJwt, requestDemoToken, type DemoRole } from './demo/tokens'
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
  const [token, setToken] = useState<string | null>(null)
  const [selecting, setSelecting] = useState(false)
  const selection = useRef(0)
  const claims = token ? decodeJwt(token) : null

  useEffect(() => {
    setUnauthorizedHandler(() => {
      selection.current += 1
      setToken(null)
      setRole(null)
      setPage('events')
      setSessionVersion((version) => version + 1)
      setMessage('La sesión de prueba venció o fue rechazada. Seleccioná un perfil para continuar.')
    })
    return () => setUnauthorizedHandler(null)
  }, [])

  async function chooseRole(nextRole: DemoRole | null) {
    const currentSelection = ++selection.current
    setActiveToken(null)
    setToken(null)
    setRole(null)
    setSelecting(!!nextRole)
    setPage('events')
    setSessionVersion((version) => version + 1)
    if (!nextRole) { setMessage(''); return }
    try {
      const nextToken = await requestDemoToken(nextRole)
      if (selection.current !== currentSelection) return
      setActiveToken(nextToken)
      setToken(nextToken)
      setMessage('')
    } catch (error) {
      if (selection.current !== currentSelection) return
      setMessage(error instanceof Error ? error.message : 'No se pudo obtener un JWT de prueba.')
      return
    } finally {
      if (selection.current === currentSelection) setSelecting(false)
    }
    setRole(nextRole)
    setPage(nextRole === 'ADMIN' || nextRole === 'ORGANIZER' ? 'operations' : 'events')
  }

  useEffect(() => {
    if (!role || !claims) return
    const currentSelection = selection.current
    let cancelled = false
    let retryTimer: number | undefined
    const renew = async () => {
      try {
        const nextToken = await requestDemoToken(role)
        if (cancelled || selection.current !== currentSelection) return
        renewActiveToken(nextToken)
        setToken(nextToken)
        setMessage('')
      } catch {
        if (cancelled || selection.current !== currentSelection) return
        if (claims.exp * 1000 <= Date.now()) {
          setActiveToken(null)
          setRole(null)
          setToken(null)
          setPage('events')
          setMessage('La sesión de prueba venció y no se pudo renovar. Seleccioná el perfil nuevamente.')
        } else {
          setMessage('No se pudo renovar el JWT de prueba. Se reintentará antes del vencimiento.')
          retryTimer = window.setTimeout(renew, 30_000)
        }
      }
    }
    const delay = Math.max(0, claims.exp * 1000 - Date.now() - 300_000)
    const timer = window.setTimeout(renew, delay)
    return () => { cancelled = true; window.clearTimeout(timer); window.clearTimeout(retryTimer) }
  }, [role, token])

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
            <select id="demo-profile" value={role ?? ''} disabled={selecting} onChange={(event) => void chooseRole(event.target.value as DemoRole || null)}>
              <option value="">Sin sesión</option>
              <option value="CLIENT">Cliente</option>
              <option value="ORGANIZER">Organizador</option>
              <option value="ADMIN">Administrador</option>
            </select>
            {role && <button type="button" className="button-quiet" onClick={() => void chooseRole(null)}>Salir</button>}
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
