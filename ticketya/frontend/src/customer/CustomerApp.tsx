import { useState } from 'react'
import { EventBrowser } from './EventBrowser'
import { MyTickets } from './MyTickets'
import { PreferencesView } from './PreferencesView'
import { Api, Order } from './types'

export function CustomerApp({ api, profileKey }: { api: Api; profileKey: string }) {
  const [tab, setTab] = useState<'events' | 'tickets' | 'preferences'>('events')
  const [openEventId, setOpenEventId] = useState<string>()
  const [latestOrder, setLatestOrder] = useState<Order | null>(null)

  return <main className="customer-area">
    <nav aria-label="Cliente" className="area-nav">
      <button aria-current={tab === 'events' ? 'page' : undefined} onClick={() => setTab('events')}>Buscar eventos</button>
      {profileKey && <><button aria-current={tab === 'tickets' ? 'page' : undefined} onClick={() => setTab('tickets')}>Mis entradas</button><button aria-current={tab === 'preferences' ? 'page' : undefined} onClick={() => setTab('preferences')}>Preferencias</button></>}
    </nav>
    {latestOrder && tab === 'events' && <p role="status">Última orden: {latestOrder.orderId} · {latestOrder.status}</p>}
    {tab === 'events' && <EventBrowser api={api} profileKey={profileKey} onOrder={setLatestOrder} openEventId={openEventId} />}
    {tab === 'tickets' && profileKey && <MyTickets api={api} />}
    {tab === 'preferences' && profileKey && <PreferencesView api={api} openEvent={id => { setOpenEventId(id); setTab('events') }} />}
  </main>
}
