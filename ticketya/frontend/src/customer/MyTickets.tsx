import { useEffect, useState } from 'react'
import { Api, EventDetail, Order, Page, Refund, errorMessage, formatDate, formatMoney } from './types'

type CurrentDate = { value?: string; unavailable?: true }

export function MyTickets({ api }: { api: Api }) {
  const [page, setPage] = useState(0)
  const [orders, setOrders] = useState<Page<Order> | null>(null)
  const [selected, setSelected] = useState<Order | null>(null)
  const [dates, setDates] = useState<Record<string, CurrentDate>>({})
  const [refunds, setRefunds] = useState<Record<string, Refund>>({})
  const [error, setError] = useState('')

  useEffect(() => {
    const controller = new AbortController()
    api<Page<Order>>(`/api/orders?page=${page}&size=10`, { signal: controller.signal }).then(setOrders).catch(e => {
      if (!controller.signal.aborted) setError(errorMessage(e))
    })
    return () => controller.abort()
  }, [api, page])

  useEffect(() => {
    if (!orders) return
    const controller = new AbortController()
    const uniqueIds = [...new Set(orders.items.map(order => order.eventId))]
    Promise.all(uniqueIds.map(async id => {
      try {
        const event = await api<EventDetail>(`/api/events/${id}`, { signal: controller.signal })
        return [id, { value: event.startsAt }] as const
      } catch {
        return [id, { unavailable: true }] as const
      }
    })).then(entries => { if (!controller.signal.aborted) setDates(Object.fromEntries(entries)) })
    return () => controller.abort()
  }, [api, orders])

  async function loadDetail(id: string) {
    setError('')
    try { setSelected(await api<Order>(`/api/orders/${id}`)) }
    catch (e) { setError(errorMessage(e)) }
  }

  async function requestRefund(ticketId: string) {
    setError('')
    try {
      const refund = await api<Refund>(`/api/refunds/tickets/${ticketId}`, { method: 'POST' })
      setRefunds(current => ({ ...current, [ticketId]: refund }))
    } catch (e) { setError(`${errorMessage(e)} El estado del ticket no se modificó.`) }
  }

  async function refreshRefund(ticketId: string, refundId: string) {
    setError('')
    try {
      const refund = await api<Refund>(`/api/refunds/${refundId}`)
      setRefunds(current => ({ ...current, [ticketId]: refund }))
    } catch (e) { setError(errorMessage(e)) }
  }

  return <section aria-label="Mis entradas">
    <h2>Mis entradas</h2>
    <p>Las entradas se consultan desde tus órdenes. Sólo se muestra QR si el ticket fue emitido.</p>
    {error && <p role="alert">{error}</p>}
    {orders && <><p>{orders.totalElements} órdenes</p>{orders.items.length === 0 && <p>Todavía no hay órdenes.</p>}
      <ul>{orders.items.map(order => <li key={order.orderId}>
        <button onClick={() => void loadDetail(order.orderId)}>{order.eventName} · {order.orderId}</button> · {order.status} · {formatMoney(order.totalAmount)}
        <p>Fecha histórica al comprar: {formatDate(order.eventStartsAt)}</p>
        {dates[order.eventId]?.value ? <p>Fecha vigente del evento: <strong>{formatDate(dates[order.eventId].value!)}</strong></p> :
          <p role="status">{dates[order.eventId]?.unavailable ? 'No se pudo obtener la fecha vigente desde Catalog.' : 'Consultando fecha vigente…'}</p>}
      </li>)}</ul>
      <div><button disabled={page === 0} onClick={() => setPage(page - 1)}>Anterior</button> Página {orders.page + 1} de {Math.max(orders.totalPages, 1)} <button disabled={page + 1 >= orders.totalPages} onClick={() => setPage(page + 1)}>Siguiente</button></div>
    </>}
    {selected && <article><h3>Orden {selected.orderId}</h3><p>Estado: {selected.status}</p>
      {selected.items.map((item, index) => <section key={index}><h4>{item.sectorName} · {item.quantity} entradas</h4>
        {item.tickets.length === 0 && <p>Esta orden todavía no tiene tickets emitidos.</p>}
        {item.tickets.map(ticket => <div key={ticket.ticketId} className="ticket">
          <p>Ticket {ticket.ticketId} · {ticket.status}{ticket.row && ` · ${ticket.row}-${ticket.seatNumber}`}</p>
          {ticket.status === 'ISSUED' && ticket.qrPngBase64 && <img width="180" height="180" alt={`QR del ticket ${ticket.ticketId}`} src={`data:image/png;base64,${ticket.qrPngBase64}`} />}
          <div><button disabled={ticket.status !== 'ISSUED'} onClick={() => void requestRefund(ticket.ticketId)}>Solicitar devolución</button>
            {refunds[ticket.ticketId] && <><span> Devolución {refunds[ticket.ticketId].id}: {refunds[ticket.ticketId].status} · {refunds[ticket.ticketId].progress}</span><button onClick={() => void refreshRefund(ticket.ticketId, refunds[ticket.ticketId].id)}>Consultar estado</button></>}
          </div>
        </div>)}
      </section>)}
    </article>}
  </section>
}
