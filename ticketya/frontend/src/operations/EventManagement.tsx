import { useEffect, useState, type FormEvent } from 'react'
import { operationsApi, scheduleValidation, type EventInput, type EventReport, type ManagedEvent, type Role, type Venue } from './api'

const dateLabel = (value: string) => new Date(value).toLocaleString('es-AR')
const inputDate = (value: string) => value ? new Date(value).toISOString().slice(0, 16) : ''
const money = (value: number) => new Intl.NumberFormat('es-AR', { style: 'currency', currency: 'ARS' }).format(value)
const errorMessage = (error: unknown) => error instanceof Error ? error.message : 'No se pudo completar la operación.'
const emptyForm: EventInput = {
  name: '', description: '', eventType: 'MUSIC', startsAt: '', venueId: '', participants: [], offerings: [], highDemand: false,
}

export default function EventManagement({ role }: { role: Role; userId: string }) {
  const [events, setEvents] = useState<ManagedEvent[]>([])
  const [venues, setVenues] = useState<Venue[]>([])
  const [selected, setSelected] = useState<ManagedEvent | null>(null)
  const [form, setForm] = useState<EventInput>(emptyForm)
  const [participantsText, setParticipantsText] = useState('')
  const [report, setReport] = useState<EventReport | null>(null)
  const [scheduleDate, setScheduleDate] = useState('')
  const [scheduleIntent, setScheduleIntent] = useState<{ eventId: string; startsAt: string; revision: number; key: string } | null>(null)
  const [message, setMessage] = useState('')
  const [error, setError] = useState('')
  const [busy, setBusy] = useState(false)

  async function refresh(selectedId?: string) {
    const [newEvents, allVenues] = await Promise.all([operationsApi.events(), operationsApi.allVenues()])
    setEvents(newEvents)
    setVenues(allVenues)
    if (selectedId) {
      const current = await operationsApi.event(selectedId)
      setSelected(current)
      return current
    }
    return null
  }
  useEffect(() => {
    let live = true
    Promise.all([operationsApi.events(), operationsApi.allVenues()]).then(([newEvents, allVenues]) => {
      if (live) { setEvents(newEvents); setVenues(allVenues) }
    }).catch((cause: unknown) => { if (live) setError(errorMessage(cause)) })
    return () => { live = false }
  }, [])

  function choose(event: ManagedEvent | null) {
    setSelected(event)
    setReport(null)
    setScheduleDate('')
    setScheduleIntent(null)
    setError('')
    setMessage('')
    setParticipantsText(event?.participants.join(', ') ?? '')
    setForm(event ? {
      name: event.name, description: event.description, eventType: event.eventType,
      startsAt: inputDate(event.startsAt), venueId: event.venueId,
      participants: event.participants, offerings: event.offerings, highDemand: event.highDemand,
      ...(role === 'ADMIN' ? { organizerId: event.organizerId } : {}),
    } : { ...emptyForm, organizerId: role === 'ADMIN' ? '' : undefined })
  }

  const venue = venues.find(item => item.id === form.venueId)
  const editing = !selected || selected.status === 'DRAFT'
  function updateOffering(sectorId: string, enabled: boolean, price = 0) {
    setForm(current => ({
      ...current,
      offerings: enabled
        ? [...current.offerings.filter(item => item.sectorId !== sectorId), { sectorId, price }]
        : current.offerings.filter(item => item.sectorId !== sectorId),
    }))
  }

  async function save(event: FormEvent) {
    event.preventDefault()
    if (!editing) return
    setBusy(true); setError(''); setMessage('')
    try {
      const body: EventInput = {
        ...form,
        startsAt: new Date(form.startsAt).toISOString(),
        participants: participantsText.split(',').map(value => value.trim()).filter(Boolean),
        offerings: form.offerings,
        ...(role === 'ADMIN' ? { organizerId: form.organizerId } : {}),
      }
      let saved: ManagedEvent
      if (selected) {
        await operationsApi.updateEvent(selected.id, body)
        saved = await operationsApi.offerings(selected.id, body.offerings)
      } else {
        saved = await operationsApi.createEvent(body)
      }
      await refresh(saved.id)
      setMessage('Borrador guardado. Las ofertas por sector quedaron actualizadas.')
    } catch (cause) { setError(errorMessage(cause)) }
    finally { setBusy(false) }
  }

  async function publish() {
    if (!selected || !window.confirm(`¿Publicar "${selected.name}"? El borrador y el snapshot dejarán de ser editables.`)) return
    setBusy(true); setError(''); setMessage('')
    try {
      const published = await operationsApi.publish(selected.id)
      await refresh(published.id)
      setMessage(`Evento publicado: ${published.status}`)
    } catch (cause) { setError(errorMessage(cause)) }
    finally { setBusy(false) }
  }

  async function loadReport() {
    if (!selected) return
    setBusy(true); setError('')
    try { setReport(await operationsApi.report(selected.id)) }
    catch (cause) { setError(errorMessage(cause)) }
    finally { setBusy(false) }
  }

  async function transition(action: 'suspend' | 'resume' | 'cancel') {
    if (!selected || !window.confirm(`¿Confirmar ${action === 'cancel' ? 'cancelación y reintegros' : action === 'suspend' ? 'suspensión' : 'reanudación'} de "${selected.name}"?`)) return
    setBusy(true); setError(''); setMessage('')
    try {
      if (action === 'cancel') {
        const progress = await operationsApi.cancel(selected.id)
        setMessage(`Cancelación: ${progress.status}. Reintegrados ${progress.completedTickets} de ${progress.totalTickets}; pendientes ${progress.pendingTickets}.`)
      } else {
        const changed = action === 'suspend' ? await operationsApi.suspend(selected.id) : await operationsApi.resume(selected.id)
        setMessage(`Estado confirmado: ${changed.status}`)
      }
      await refresh(selected.id)
    } catch (cause) {
      setError(errorMessage(cause))
      try { await refresh(selected.id) } catch { /* Se conserva el error original. */ }
    } finally { setBusy(false) }
  }

  async function postpone(event: FormEvent) {
    event.preventDefault()
    if (!selected) return
    const timestamp = Date.parse(scheduleDate)
    if (!Number.isFinite(timestamp)) { setError('Ingresá una fecha válida.'); return }
    const next = new Date(timestamp).toISOString()
    const problem = scheduleValidation(next, selected.startsAt)
    if (problem) { setError(problem); return }
    if (!window.confirm(`¿Postergar "${selected.name}" hasta ${dateLabel(next)}?`)) return
    setBusy(true); setError(''); setMessage('')
    const intent = scheduleIntent?.eventId === selected.id && scheduleIntent.startsAt === next && scheduleIntent.revision === selected.scheduleRevision
      ? scheduleIntent : { eventId: selected.id, startsAt: next, revision: selected.scheduleRevision, key: crypto.randomUUID() }
    setScheduleIntent(intent)
    try {
      const changed = await operationsApi.postpone(selected.id, next, selected.scheduleRevision, intent.key)
      await refresh(selected.id)
      setScheduleDate('')
      setScheduleIntent(null)
      setMessage(`Postergación confirmada: ${dateLabel(changed.startsAt)} (revisión ${changed.scheduleRevision}).`)
    } catch (cause) {
      setError(errorMessage(cause))
      try {
        const current = await refresh(selected.id)
        if (current?.scheduleRevision !== intent.revision) setScheduleIntent(null)
      } catch { /* Se conserva el error original. */ }
    } finally { setBusy(false) }
  }

  return <div>
    <h2>{role === 'ADMIN' ? 'Gestión de eventos' : 'Mis eventos'}</h2>
    <p>Catalog define el estado, los permisos y el precio. Los cambios de cupo se consultan al comprar.</p>
    {error && <p role="alert">{error}</p>}
    {message && <p role="status">{message}</p>}
    <div className="split">
      <section>
        <button type="button" onClick={() => choose(null)}>Nuevo borrador</button>
        <button type="button" onClick={() => void refresh(selected?.id).catch(cause => setError(errorMessage(cause)))}>Actualizar lista</button>
        {events.length === 0 ? <p>No hay eventos gestionables.</p> : <ul>
          {events.map(item => <li key={item.id}>
            <button type="button" onClick={() => choose(item)} aria-current={selected?.id === item.id ? 'page' : undefined}>{item.name}</button>
            {' '}<small>{item.status} · {dateLabel(item.startsAt)}</small>
          </li>)}
        </ul>}
      </section>
      <section>
        <h3>{selected ? selected.name : 'Nuevo borrador'}</h3>
        {selected && <p>ID: {selected.id} · Estado: {selected.status} · Revisión: {selected.scheduleRevision}</p>}
        {editing ? <form onSubmit={save}>
          <label>Nombre <input required maxLength={200} value={form.name} onChange={e => setForm({ ...form, name: e.target.value })} /></label>
          <label>Descripción <textarea required value={form.description} onChange={e => setForm({ ...form, description: e.target.value })} /></label>
          <label>Tipo <input required maxLength={80} value={form.eventType} onChange={e => setForm({ ...form, eventType: e.target.value })} /></label>
          <label>Fecha y hora <input type="datetime-local" required value={form.startsAt} onChange={e => setForm({ ...form, startsAt: e.target.value })} /></label>
          <label>Sede <select required value={form.venueId} onChange={e => setForm({ ...form, venueId: e.target.value, offerings: [] })}>
            <option value="">Seleccionar sede</option>
            {venues.map(item => <option key={item.id} value={item.id}>{item.name} · {item.city}</option>)}
          </select></label>
          {role === 'ADMIN' && <label>UUID del organizador <input required value={form.organizerId ?? ''} onChange={e => setForm({ ...form, organizerId: e.target.value })} /></label>}
          <label>Participantes (separados por coma) <input value={participantsText} onChange={e => setParticipantsText(e.target.value)} /></label>
          <label><input type="checkbox" checked={form.highDemand} onChange={e => setForm({ ...form, highDemand: e.target.checked })} /> Alta demanda (requiere fila virtual)</label>
          <fieldset><legend>Sectores ofrecidos y precios</legend>
            {!venue && <p>Seleccioná una sede para ofrecer sectores.</p>}
            {venue?.sectors.map(sector => {
              const offer = form.offerings.find(item => item.sectorId === sector.id)
              return <div key={sector.id}>
                <label><input type="checkbox" checked={!!offer} onChange={e => updateOffering(sector.id, e.target.checked, 1)} /> {sector.name} ({sector.type}, capacidad {sector.capacity})</label>
                {offer && <label>Precio ARS <input type="number" min="0.01" step="0.01" required value={offer.price} onChange={e => updateOffering(sector.id, true, Number(e.target.value))} /></label>}
              </div>
            })}
          </fieldset>
          <button disabled={busy} type="submit">Guardar borrador</button>
        </form> : <p>Este evento ya no está en DRAFT. Sus datos, sede y ofertas no se editan desde esta pantalla.</p>}
        {selected?.status === 'DRAFT' && <button disabled={busy} type="button" onClick={() => void publish()}>Publicar</button>}
        {selected && <button disabled={busy} type="button" onClick={() => void loadReport()}>Ver reporte</button>}
        {role === 'ADMIN' && selected?.status === 'PUBLISHED' && <button disabled={busy} type="button" onClick={() => void transition('suspend')}>Suspender</button>}
        {role === 'ADMIN' && selected?.status === 'SUSPENDED' && <button disabled={busy} type="button" onClick={() => void transition('resume')}>Reanudar</button>}
        {role === 'ADMIN' && (selected?.status === 'PUBLISHED' || selected?.status === 'SUSPENDED') && <button disabled={busy} type="button" onClick={() => void transition('cancel')}>Cancelar e iniciar reintegros</button>}
        {selected?.status === 'PUBLISHED' && <form onSubmit={postpone}>
          <h4>Postergar fecha u horario</h4>
          <p>Debe ser posterior a {dateLabel(selected.startsAt)} y superar 72 horas desde ahora.</p>
          <label>Nueva fecha y hora <input type="datetime-local" required value={scheduleDate} onChange={e => setScheduleDate(e.target.value)} /></label>
          <button type="submit" disabled={busy}>Confirmar postergación</button>
        </form>}
        {report && <section>
          <h4>Reporte · {report.currency}</h4>
          {!report.capacityAvailable && <p role="status">Capacidad y ocupación no disponibles. {report.warnings.join(' ')}</p>}
          <table><thead><tr><th>Sector</th><th>Vendidas</th><th>Devueltas</th><th>Activas</th><th>Capacidad</th><th>Ocupación</th><th>Bruto</th><th>Reintegrado</th><th>Neto</th></tr></thead>
            <tbody>{report.sectors.map(row => <tr key={row.sourceSectorId}><th>{row.sectorName ?? row.sourceSectorId}</th><td>{row.soldTickets}</td><td>{row.refundedTickets}</td><td>{row.activeTickets}</td><td>{report.capacityAvailable ? row.capacity ?? '—' : '—'}</td><td>{report.capacityAvailable && typeof row.occupancyPercent === 'number' ? `${row.occupancyPercent}%` : '—'}</td><td>{money(row.grossRevenue)}</td><td>{money(row.refundedAmount)}</td><td>{money(row.netRevenue)}</td></tr>)}</tbody>
            <tfoot><tr><th>Total</th><td>{report.totals.soldTickets}</td><td>{report.totals.refundedTickets}</td><td>{report.totals.activeTickets}</td><td>{report.capacityAvailable ? report.totals.capacity ?? '—' : '—'}</td><td>{report.capacityAvailable && typeof report.totals.occupancyPercent === 'number' ? `${report.totals.occupancyPercent}%` : '—'}</td><td>{money(report.totals.grossRevenue)}</td><td>{money(report.totals.refundedAmount)}</td><td>{money(report.totals.netRevenue)}</td></tr></tfoot>
          </table>
        </section>}
      </section>
    </div>
  </div>
}
