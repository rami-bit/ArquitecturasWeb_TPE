import { useState } from 'react'
import EventManagement from './EventManagement'
import { AuditTool, RefundDecisionTool, VenueManagement } from './AdminTools'
import type { Role } from './api'
import './operations.css'

type Section = 'events' | 'venues' | 'refunds' | 'audit'

export default function OperationsArea({ role, userId }: { role: Role; userId: string }) {
  const [section, setSection] = useState<Section>('events')
  const active = role === 'ADMIN' ? section : 'events'
  return <div className="operations">
    <nav aria-label="Operaciones">
      <button type="button" aria-current={active === 'events' ? 'page' : undefined} onClick={() => setSection('events')}>Eventos</button>
      {role === 'ADMIN' && <>
        <button type="button" aria-current={active === 'venues' ? 'page' : undefined} onClick={() => setSection('venues')}>Sedes</button>
        <button type="button" aria-current={active === 'refunds' ? 'page' : undefined} onClick={() => setSection('refunds')}>Devoluciones</button>
        <button type="button" aria-current={active === 'audit' ? 'page' : undefined} onClick={() => setSection('audit')}>Auditoría</button>
      </>}
    </nav>
    {active === 'events' && <EventManagement role={role} userId={userId} />}
    {active === 'venues' && role === 'ADMIN' && <VenueManagement />}
    {active === 'refunds' && role === 'ADMIN' && <RefundDecisionTool />}
    {active === 'audit' && role === 'ADMIN' && <AuditTool />}
  </div>
}
