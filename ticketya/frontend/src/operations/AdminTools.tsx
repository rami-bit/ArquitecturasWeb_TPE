import { useEffect, useState, type FormEvent } from 'react'
import { operationsApi, type AuditEvent, type AuditPage, type AuditTrace, type RefundDecision, type Seat, type Venue, type VenueInput } from './api'

const errorMessage = (error: unknown) => error instanceof Error ? error.message : 'No se pudo completar la operación.'
const emptyVenue: VenueInput = { name: '', address: '', city: '', latitude: 0, longitude: 0, sectors: [] }
type SectorInput = VenueInput['sectors'][number]
const emptySector = (): SectorInput => ({ name: '', type: 'GENERAL_ADMISSION', capacity: 1, seats: [] })

function seatText(seats: Seat[]) { return seats.map(seat => `${seat.rowLabel}:${seat.seatNumber}`).join(', ') }
function parseSeats(value: string): Seat[] {
  return value.split(',').map(part => part.trim()).filter(Boolean).map(part => {
    const [rowLabel, rawNumber] = part.split(':')
    const seatNumber = Number(rawNumber)
    if (!rowLabel || !Number.isInteger(seatNumber) || seatNumber < 1) throw new Error('Asientos: usá el formato Fila:Número, separados por coma.')
    return { rowLabel, seatNumber }
  })
}

export function VenueManagement() {
  const [venues, setVenues] = useState<Venue[]>([])
  const [page, setPage] = useState(0)
  const [totalPages, setTotalPages] = useState(0)
  const [selectedId, setSelectedId] = useState<string | null>(null)
  const [form, setForm] = useState<VenueInput>(emptyVenue)
  const [seatTexts, setSeatTexts] = useState<string[]>([])
  const [error, setError] = useState('')
  const [message, setMessage] = useState('')
  const [busy, setBusy] = useState(false)
  async function refresh(nextPage = page) {
    const result = await operationsApi.venues(nextPage)
    setVenues(result.content); setPage(result.page); setTotalPages(result.totalPages)
  }
  useEffect(() => { void refresh(0).catch(cause => setError(errorMessage(cause))) }, [])
  function choose(venue: Venue | null) {
    setSelectedId(venue?.id ?? null)
    setForm(venue ? {
      name: venue.name, address: venue.address, city: venue.city,
      latitude: venue.latitude, longitude: venue.longitude,
      sectors: venue.sectors.map(({ name, type, capacity, seats }) => ({ name, type, capacity, seats })),
    } : emptyVenue)
    setSeatTexts(venue?.sectors.map(sector => seatText(sector.seats)) ?? [])
    setError(''); setMessage('')
  }
  function sectorAt(index: number, patch: Partial<SectorInput>) {
    setForm(current => ({ ...current, sectors: current.sectors.map((sector, i) => i === index ? { ...sector, ...patch } : sector) }))
  }
  async function save(event: FormEvent) {
    event.preventDefault()
    setBusy(true); setError(''); setMessage('')
    try {
      const body = {
        ...form,
        sectors: form.sectors.map((sector, index) => ({
          ...sector, seats: sector.type === 'NUMBERED' ? parseSeats(seatTexts[index] ?? '') : [],
        })),
      }
      const saved = selectedId ? await operationsApi.updateVenue(selectedId, body) : await operationsApi.createVenue(body)
      setSelectedId(saved.id)
      await refresh()
      setMessage(`Sede guardada: ${saved.name} (${saved.id})`)
    } catch (cause) { setError(errorMessage(cause)) }
    finally { setBusy(false) }
  }
  return <section>
    <h2>Sedes y sectores</h2>
    {error && <p role="alert">{error}</p>}{message && <p role="status">{message}</p>}
    <button type="button" onClick={() => choose(null)}>Nueva sede</button>
    <ul>{venues.map(venue => <li key={venue.id}><button type="button" onClick={() => choose(venue)}>{venue.name} · {venue.city}</button></li>)}</ul>
    <button disabled={page === 0} type="button" onClick={() => void refresh(page - 1)}>Anterior</button>
    <span> Página {page + 1} de {Math.max(1, totalPages)} </span>
    <button disabled={page + 1 >= totalPages} type="button" onClick={() => void refresh(page + 1)}>Siguiente</button>
    <form onSubmit={save}>
      <h3>{selectedId ? 'Editar sede' : 'Crear sede'}</h3>
      <label>Nombre <input required value={form.name} onChange={e => setForm({ ...form, name: e.target.value })} /></label>
      <label>Dirección <input required value={form.address} onChange={e => setForm({ ...form, address: e.target.value })} /></label>
      <label>Ciudad <input required value={form.city} onChange={e => setForm({ ...form, city: e.target.value })} /></label>
      <label>Latitud <input type="number" min="-90" max="90" step="any" required value={form.latitude} onChange={e => setForm({ ...form, latitude: Number(e.target.value) })} /></label>
      <label>Longitud <input type="number" min="-180" max="180" step="any" required value={form.longitude} onChange={e => setForm({ ...form, longitude: Number(e.target.value) })} /></label>
      <fieldset><legend>Sectores</legend>
        {form.sectors.map((sector, index) => <div key={index}>
          <label>Nombre <input required value={sector.name} onChange={e => sectorAt(index, { name: e.target.value })} /></label>
          <label>Tipo <select value={sector.type} onChange={e => sectorAt(index, { type: e.target.value as SectorInput['type'] })}>
            <option value="GENERAL_ADMISSION">General</option><option value="NUMBERED">Numerado</option>
          </select></label>
          <label>Capacidad <input type="number" min="1" required value={sector.capacity} onChange={e => sectorAt(index, { capacity: Number(e.target.value) })} /></label>
          {sector.type === 'NUMBERED' && <label>Asientos (A:1, A:2) <input value={seatTexts[index] ?? ''} onChange={e => setSeatTexts(current => current.map((value, i) => i === index ? e.target.value : value))} /></label>}
          <button type="button" onClick={() => {
            setForm(current => ({ ...current, sectors: current.sectors.filter((_, i) => i !== index) }))
            setSeatTexts(current => current.filter((_, i) => i !== index))
          }}>Quitar sector</button>
        </div>)}
        <button type="button" onClick={() => { setForm(current => ({ ...current, sectors: [...current.sectors, emptySector()] })); setSeatTexts(current => [...current, '']) }}>Agregar sector</button>
      </fieldset>
      <button disabled={busy} type="submit">Guardar sede</button>
    </form>
  </section>
}

export function RefundDecisionTool() {
  const [refundId, setRefundId] = useState('')
  const [decision, setDecision] = useState<'APPROVED' | 'REJECTED'>('APPROVED')
  const [result, setResult] = useState<RefundDecision | null>(null)
  const [error, setError] = useState('')
  const [busy, setBusy] = useState(false)
  async function submit(event: FormEvent) {
    event.preventDefault()
    if (!window.confirm(`¿Confirmar decisión ${decision} para la solicitud ${refundId}?`)) return
    setBusy(true); setError(''); setResult(null)
    try { setResult(await operationsApi.decideRefund(refundId, decision)) }
    catch (cause) { setError(errorMessage(cause)) }
    finally { setBusy(false) }
  }
  return <section>
    <h2>Decidir devolución por ID conocido</h2>
    <p>La API no ofrece una bandeja ni una consulta previa administrativa. Ingresá el ID recibido por otro medio.</p>
    <form onSubmit={submit}>
      <label>UUID de solicitud <input required value={refundId} onChange={e => setRefundId(e.target.value)} /></label>
      <label>Decisión <select value={decision} onChange={e => setDecision(e.target.value as 'APPROVED' | 'REJECTED')}><option value="APPROVED">Aprobar</option><option value="REJECTED">Rechazar</option></select></label>
      <button disabled={busy} type="submit">Confirmar decisión</button>
    </form>
    {error && <p role="alert">{error}</p>}
    {result && <p role="status">Solicitud {result.id}: {result.status}, decisión {result.decision}, progreso {result.progress}. Monto: {result.amount} ARS.</p>}
  </section>
}

const auditFilters = ['correlationId', 'service', 'operation', 'outcome', 'resourceId', 'actorId', 'from', 'to'] as const
type FilterName = typeof auditFilters[number]
const blankFilters = Object.fromEntries(auditFilters.map(name => [name, ''])) as Record<FilterName, string>
function AuditRows({ events, selectTrace }: { events: AuditEvent[]; selectTrace: (id: string) => void }) {
  return <table><thead><tr><th>Fecha</th><th>Servicio</th><th>Operación</th><th>Resultado</th><th>Recurso</th><th>Correlación</th></tr></thead><tbody>
    {events.map(item => <tr key={item.eventId}><td>{new Date(item.occurredAt).toLocaleString('es-AR')}</td><td>{item.service}</td><td>{item.operation}</td><td>{item.outcome}</td><td>{item.resourceType} {item.resourceId}</td><td><button type="button" onClick={() => selectTrace(item.correlationId)}>{item.correlationId}</button></td></tr>)}
  </tbody></table>
}
export function AuditTool() {
  const [filters, setFilters] = useState(blankFilters)
  const [page, setPage] = useState<AuditPage | null>(null)
  const [trace, setTrace] = useState<AuditTrace | null>(null)
  const [error, setError] = useState('')
  async function search(nextPage = 0) {
    setError('')
    const params = new URLSearchParams({ page: String(nextPage), size: '20' })
    for (const name of auditFilters) if (filters[name]) params.set(name, name === 'from' || name === 'to' ? new Date(filters[name]).toISOString() : filters[name])
    try { setPage(await operationsApi.audit(params)); setTrace(null) }
    catch (cause) { setError(errorMessage(cause)) }
  }
  async function openTrace(id: string) {
    setError('')
    try { setTrace(await operationsApi.trace(id)) }
    catch (cause) { setError(errorMessage(cause)) }
  }
  return <section>
    <h2>Auditoría</h2>
    <form onSubmit={event => { event.preventDefault(); void search() }}>
      {auditFilters.map(name => <label key={name}>{name} <input type={name === 'from' || name === 'to' ? 'datetime-local' : 'text'} value={filters[name]} onChange={event => setFilters({ ...filters, [name]: event.target.value })} /></label>)}
      <button type="submit">Buscar</button>
    </form>
    {error && <p role="alert">{error}</p>}
    {page && <><p>{page.totalItems} registros</p><AuditRows events={page.items} selectTrace={id => void openTrace(id)} />
      <button disabled={page.page === 0} onClick={() => void search(page.page - 1)}>Anterior</button>
      <button disabled={(page.page + 1) * page.size >= page.totalItems} onClick={() => void search(page.page + 1)}>Siguiente</button></>}
    {trace && <><h3>Traza {trace.correlationId}</h3><p>{trace.eventCount} eventos · {trace.services.join(', ')}</p><AuditRows events={trace.events} selectTrace={id => void openTrace(id)} /></>}
  </section>
}
