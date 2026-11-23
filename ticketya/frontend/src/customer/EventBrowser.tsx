import { useEffect, useState } from 'react'
import { Api, Availability, EventDetail, EventSummary, Intent, Order, Page, QueueEntry, buildOrderBody, errorMessage, errorStatus, formatDate, formatMoney } from './types'

type Props = { api: Api; profileKey: string; onOrder: (order: Order) => void; openEventId?: string }
const terminal = new Set(['CONFIRMED', 'REJECTED', 'COMPENSATED'])

export function EventBrowser({ api, profileKey, onOrder, openEventId }: Props) {
  const [filters, setFilters] = useState({ q: '', type: '', organizerId: '', participant: '', city: '', from: '', to: '', sort: 'date', direction: 'asc' })
  const [page, setPage] = useState(0)
  const [results, setResults] = useState<Page<EventSummary> | null>(null)
  const [selectedId, setSelectedId] = useState('')
  const [detail, setDetail] = useState<EventDetail | null>(null)
  const [availability, setAvailability] = useState<Availability | null>(null)
  const [sectorId, setSectorId] = useState('')
  const [seatIds, setSeatIds] = useState<string[]>([])
  const [quantity, setQuantity] = useState(1)
  const [queue, setQueue] = useState<QueueEntry | null>(null)
  const [now, setNow] = useState(Date.now())
  const [paymentToken, setPaymentToken] = useState('tok_approved')
  const [intent, setIntent] = useState<Intent | null>(null)
  const [order, setOrder] = useState<Order | null>(null)
  const [purchaseOutcome, setPurchaseOutcome] = useState('')
  const [error, setError] = useState('')
  const [busy, setBusy] = useState(false)
  const selected = availability?.sectors.find(sector => sector.sectorId === sectorId)
  const catalogSector = detail?.sectors.find(sector => sector.id === selected?.sourceSectorId)
  const admitted = queue?.status === 'ADMITTED' && !!queue.admissionToken && !!queue.admissionExpiresAt && Date.parse(queue.admissionExpiresAt) > now
  const storageKey = `ticketya:purchase:${profileKey}`

  useEffect(() => { if (openEventId) setSelectedId(openEventId) }, [openEventId])

  useEffect(() => {
    if (!profileKey) { setIntent(null); return }
    try { setIntent(JSON.parse(sessionStorage.getItem(storageKey) || 'null') as Intent | null) } catch { setIntent(null) }
  }, [storageKey, profileKey])

  useEffect(() => {
    const params = new URLSearchParams({ page: String(page), size: '10', sort: filters.sort, direction: filters.direction })
    for (const key of ['q', 'type', 'organizerId', 'participant', 'city', 'from', 'to'] as const) {
      const value = filters[key].trim()
      if (value) params.set(key, key === 'from' || key === 'to' ? new Date(value).toISOString() : value)
    }
    const controller = new AbortController()
    api<Page<EventSummary>>(`/api/events?${params}`, { signal: controller.signal }).then(setResults).catch(e => {
      if (!controller.signal.aborted) setError(errorMessage(e))
    })
    return () => controller.abort()
  }, [api, page, filters])

  useEffect(() => {
    if (!selectedId) return
    const controller = new AbortController()
    setDetail(null); setAvailability(null); setSectorId(''); setSeatIds([]); setQueue(null); setError('')
    Promise.all([
      api<EventDetail>(`/api/events/${selectedId}`, { signal: controller.signal }),
      api<Availability>(`/api/inventory/events/${selectedId}/availability`, { signal: controller.signal }),
    ]).then(([event, stock]) => { setDetail(event); setAvailability(stock) }).catch(e => {
      if (!controller.signal.aborted) setError(errorMessage(e))
    })
    return () => controller.abort()
  }, [api, selectedId])

  useEffect(() => {
    if (!selectedId || !detail?.highDemand || !profileKey || !queue || !['WAITING', 'ADMITTED'].includes(queue.status)) return
    const interval = window.setInterval(() => {
      api<QueueEntry>(`/api/queue/events/${selectedId}/entries/me`).then(setQueue).catch(e => setError(errorMessage(e)))
    }, 5000)
    return () => window.clearInterval(interval)
  }, [api, selectedId, detail?.highDemand, profileKey, queue?.status])

  useEffect(() => {
    if (queue?.status !== 'ADMITTED') return
    const interval = window.setInterval(() => setNow(Date.now()), 1000)
    return () => window.clearInterval(interval)
  }, [queue?.status])

  function updateFilter(name: keyof typeof filters, value: string) {
    setPage(0)
    setFilters(current => ({ ...current, [name]: value }))
  }

  async function refreshAvailability() {
    if (!selectedId) return
    try { setAvailability(await api<Availability>(`/api/inventory/events/${selectedId}/availability`)) }
    catch (e) { setError(errorMessage(e)) }
  }

  async function joinQueue() {
    setError('')
    try { setQueue(await api<QueueEntry>(`/api/queue/events/${selectedId}/entries`, { method: 'POST' })); setNow(Date.now()) }
    catch (e) { setError(errorMessage(e)) }
  }

  async function sendIntent(next: Intent) {
    setBusy(true); setError(''); setPurchaseOutcome('')
    try {
      const response = await api<Order>('/api/orders', { method: 'POST', headers: {
        'Content-Type': 'application/json', 'Idempotency-Key': next.key,
        ...(next.admissionToken ? { 'X-Admission-Token': next.admissionToken } : {}),
      }, body: next.body })
      setOrder(response); onOrder(response)
      if (terminal.has(response.status)) { sessionStorage.removeItem(storageKey); setIntent(null); if (detail?.highDemand) setQueue(null) }
      else { const saved = { ...next, orderId: response.orderId }; sessionStorage.setItem(storageKey, JSON.stringify(saved)); setIntent(saved) }
    } catch (e) {
      setError(errorMessage(e))
      const status = errorStatus(e)
      const problem = e && typeof e === 'object' && 'problem' in e ? (e as { problem?: Record<string, unknown> }).problem : undefined
      if (status === 402) setPurchaseOutcome(`Pago rechazado. Orden ${String(problem?.orderId ?? 'sin identificador')}: REJECTED; no se emitieron entradas.`)
      if (status === 409 && problem?.orderStatus === 'COMPENSATED') setPurchaseOutcome('La orden fue compensada y el pago reintegrado.')
      if (status === 409) {
        await refreshAvailability()
        setSeatIds([])
        setError(`${errorMessage(e)} Revisá la disponibilidad antes de iniciar otra compra.`)
      }
      if (detail?.highDemand && [400, 402, 403, 404, 409].includes(status ?? 0)) setQueue(null)
      if (status === 402 || status === 400 || status === 403 || status === 404 || status === 409) {
        sessionStorage.removeItem(storageKey); setIntent(null)
      }
    } finally { setBusy(false) }
  }

  function purchase() {
    if (!detail || !selected || !profileKey || busy || intent) return
    if (selected.type === 'NUMBERED' && seatIds.length === 0) { setError('Elegí al menos un asiento disponible.'); return }
    if (selected.type === 'GENERAL_ADMISSION' && (!Number.isInteger(quantity) || quantity < 1 || quantity > selected.available)) { setError('Elegí una cantidad disponible.'); return }
    if (detail.highDemand && !admitted) { setError('Necesitás un turno admitido y vigente.'); return }
    const next = { key: crypto.randomUUID(), body: buildOrderBody(detail.id, selected, seatIds, quantity, paymentToken), ...(detail.highDemand ? { admissionToken: queue!.admissionToken! } : {}) }
    sessionStorage.setItem(storageKey, JSON.stringify(next)); setIntent(next)
    void sendIntent(next)
  }

  async function reconcile() {
    if (!intent?.orderId) return
    setBusy(true); setError('')
    try {
      const response = await api<Order>(`/api/orders/${intent.orderId}/reconcile`, { method: 'POST' })
      setOrder(response); onOrder(response)
      if (terminal.has(response.status)) { sessionStorage.removeItem(storageKey); setIntent(null) }
    } catch (e) { setError(errorMessage(e)) }
    finally { setBusy(false) }
  }

  return <section aria-label="Buscar eventos">
    <h2>Eventos</h2>
    <div className="filters">
      <label>Texto <input value={filters.q} onChange={e => updateFilter('q', e.target.value)} /></label>
      <label>Tipo <input value={filters.type} onChange={e => updateFilter('type', e.target.value)} /></label>
      <label>Organizador UUID <input value={filters.organizerId} onChange={e => updateFilter('organizerId', e.target.value)} /></label>
      <label>Participante <input value={filters.participant} onChange={e => updateFilter('participant', e.target.value)} /></label>
      <label>Ciudad <input value={filters.city} onChange={e => updateFilter('city', e.target.value)} /></label>
      <label>Desde <input type="datetime-local" value={filters.from} onChange={e => updateFilter('from', e.target.value)} /></label>
      <label>Hasta <input type="datetime-local" value={filters.to} onChange={e => updateFilter('to', e.target.value)} /></label>
      <label>Orden <select value={filters.sort} onChange={e => updateFilter('sort', e.target.value)}><option value="date">Fecha</option><option value="price">Precio</option><option value="relevance">Relevancia</option></select></label>
      <label>Dirección <select value={filters.direction} onChange={e => updateFilter('direction', e.target.value)}><option value="asc">Ascendente</option><option value="desc">Descendente</option></select></label>
    </div>
    {error && <p role="alert">{error}</p>}
    {results && <><p>{results.totalElements} eventos</p>{results.items.length === 0 && <p>No hay eventos para estos filtros.</p>}
      <ul>{results.items.map(event => <li key={event.id}><button onClick={() => setSelectedId(event.id)}>{event.name}</button> · {event.city} · {formatDate(event.startsAt)} · Desde {event.minimumPrice == null ? 'sin precio' : formatMoney(event.minimumPrice)} {event.highDemand && '· Alta demanda'}</li>)}</ul>
      <div><button disabled={page === 0} onClick={() => setPage(page - 1)}>Anterior</button> Página {results.page + 1} de {Math.max(results.totalPages, 1)} <button disabled={page + 1 >= results.totalPages} onClick={() => setPage(page + 1)}>Siguiente</button></div></>}
    {detail && availability && <article>
      <h3>{detail.name}</h3><p>{detail.description}</p><p>{formatDate(detail.startsAt)} · {detail.venueName}, {detail.venueAddress}, {detail.city}</p>
      <p>Participantes: {detail.participants.join(', ') || 'Sin datos'}</p>
      <button onClick={() => void refreshAvailability()}>Actualizar disponibilidad</button>
      <p>Disponibilidad consultada: {formatDate(availability.generatedAt)}. Elegir no reserva entradas.</p>
      {availability.sectors.map(sector => {
        const price = detail.sectors.find(item => item.id === sector.sourceSectorId)?.price
        return <div key={sector.sectorId} className="sector"><label><input type="radio" name="sector" checked={sectorId === sector.sectorId} onChange={() => { setSectorId(sector.sectorId); setSeatIds([]); setQuantity(1) }} />{sector.name} · {sector.available} disponibles · {price == null ? 'Precio no disponible' : formatMoney(price)}</label>
          {sectorId === sector.sectorId && sector.type === 'GENERAL_ADMISSION' && <label>Cantidad <input type="number" min="1" max={sector.available} value={quantity} onChange={e => setQuantity(Number(e.target.value))} /></label>}
          {sectorId === sector.sectorId && sector.type === 'NUMBERED' && <div className="seat-grid">{(sector.seats || []).map(seat => <label key={seat.seatId}><input type="checkbox" disabled={seat.status !== 'AVAILABLE'} checked={seatIds.includes(seat.seatId)} onChange={e => setSeatIds(current => e.target.checked ? [...current, seat.seatId] : current.filter(id => id !== seat.seatId))} />{seat.row}-{seat.number} ({seat.status})</label>)}</div>}
        </div>
      })}
      {detail.highDemand && <div><h4>Fila virtual</h4><p>El turno no garantiza entradas.</p><button disabled={!profileKey} onClick={() => void joinQueue()}>Entrar o consultar fila</button>{queue && <p>Estado: {queue.status}; posición {queue.position}; personas delante: {queue.peopleAhead}{admitted && `; admisión hasta ${formatDate(queue.admissionExpiresAt!)}`}</p>}{queue?.status === 'ADMITTED' && !admitted && <p>La admisión venció; consultá o volvé a la fila.</p>}</div>}
      {profileKey ? <div><label>Pago de demostración <select value={paymentToken} onChange={e => setPaymentToken(e.target.value)}><option value="tok_approved">Aprobado</option><option value="tok_rejected">Rechazado</option><option value="tok_pending">Pendiente</option></select></label>
        <button disabled={busy || !!intent || !sectorId || (detail.highDemand && !admitted)} onClick={purchase}>Confirmar compra</button></div> : <p>Elegí un perfil de cliente para comprar.</p>}
      {intent && <div><p>Compra con resultado incierto. Conservamos la misma clave y el mismo pedido para evitar duplicados.</p><button disabled={busy} onClick={() => void sendIntent(intent)}>Reintentar misma intención</button>{intent.orderId && <><button disabled={busy} onClick={() => void reconcile()}>Reconciliar orden</button><button disabled={busy} onClick={() => void api<Order>(`/api/orders/${intent.orderId}`).then(setOrder).catch(e => setError(errorMessage(e)))}>Consultar orden</button></>}</div>}
      {order && <p>Orden {order.orderId}: {order.status} · {formatMoney(order.totalAmount)}{order.status === 'REJECTED' && ' · Sin entradas emitidas'}</p>}
      {purchaseOutcome && <p role="status">{purchaseOutcome}</p>}
      {catalogSector && <small>Precio informado por Catalog para el sector {catalogSector.name}.</small>}
    </article>}
  </section>
}
