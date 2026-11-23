// @vitest-environment jsdom
import { afterEach, describe, expect, it, vi } from 'vitest'
import { cleanup, fireEvent, render, screen, waitFor } from '@testing-library/react'
import { EventBrowser } from './EventBrowser'
import { MyTickets } from './MyTickets'
import { Api, Order } from './types'

afterEach(() => { cleanup(); sessionStorage.clear() })

const event = { id: 'event-1', name: 'Concierto', eventType: 'MUSIC', startsAt: '2030-01-01T20:00:00Z', highDemand: false, city: 'Rosario', minimumPrice: 100, description: 'Demo', venueName: 'Sala', venueAddress: 'Centro', participants: [], sectors: [{ id: 'catalog-sector', name: 'Platea', type: 'NUMBERED', price: 100, capacity: 1 }] }
const stock = { eventId: 'event-1', generatedAt: '2029-01-01T00:00:00Z', sectors: [{ sectorId: 'inventory-sector', sourceSectorId: 'catalog-sector', name: 'Platea', type: 'NUMBERED', available: 1, seats: [{ seatId: 'inventory-seat', row: 'A', number: 1, status: 'AVAILABLE' }] }] }

describe('recorrido del cliente', () => {
  it('compra el asiento con IDs de Inventory y conserva clave/cuerpo ante respuesta incierta', async () => {
    const posts: { body: string; key: string }[] = []
    const api = vi.fn(async (path: string, init?: RequestInit) => {
      if (path.startsWith('/api/events?')) return { items: [event], page: 0, size: 10, totalElements: 1, totalPages: 1 }
      if (path === '/api/events/event-1') return event
      if (path === '/api/inventory/events/event-1/availability') return stock
      if (path === '/api/orders') {
        const headers = init?.headers as Record<string, string>
        posts.push({ body: String(init?.body), key: headers['Idempotency-Key'] })
        if (posts.length === 1) throw new Error('No se pudo conectar con el Gateway.')
        return { orderId: 'order-1', status: 'CONFIRMED', totalAmount: 100 }
      }
      throw new Error(path)
    }) as Api
    render(<EventBrowser api={api} profileKey="buyer-1" onOrder={() => {}} />)
    fireEvent.click(await screen.findByRole('button', { name: 'Concierto' }))
    fireEvent.click(await screen.findByRole('radio', { name: /Platea/ }))
    fireEvent.click(await screen.findByRole('checkbox', { name: /A-1/ }))
    fireEvent.click(screen.getByRole('button', { name: 'Confirmar compra' }))
    await screen.findByRole('button', { name: 'Reintentar misma intención' })
    fireEvent.click(screen.getByRole('button', { name: 'Reintentar misma intención' }))
    await screen.findByText(/Orden order-1: CONFIRMED/)
    expect(posts).toHaveLength(2)
    expect(posts[0]).toEqual(posts[1])
    expect(JSON.parse(posts[0].body).items).toEqual([{ sectorId: 'inventory-sector', seatIds: ['inventory-seat'] }])
    expect(sessionStorage.getItem('ticketya:purchase:buyer-1')).toBeNull()
  })

  it('exige admisión vigente en alta demanda y envía el token sólo al comprar', async () => {
    const highDemandEvent = { ...event, highDemand: true, sectors: [{ id: 'catalog-general', name: 'General', type: 'GENERAL_ADMISSION', price: 50, capacity: 10 }] }
    const generalStock = { ...stock, sectors: [{ sectorId: 'inventory-general', sourceSectorId: 'catalog-general', name: 'General', type: 'GENERAL_ADMISSION', available: 10 }] }
    const posts: RequestInit[] = []
    const api = vi.fn(async (path: string, init?: RequestInit) => {
      if (path.startsWith('/api/events?')) return { items: [highDemandEvent], page: 0, size: 10, totalElements: 1, totalPages: 1 }
      if (path === '/api/events/event-1') return highDemandEvent
      if (path === '/api/inventory/events/event-1/availability') return generalStock
      if (path === '/api/queue/events/event-1/entries') return { status: 'ADMITTED', position: 1, peopleAhead: 0, admissionToken: 'admit-1', admissionExpiresAt: '2099-01-01T00:00:00Z' }
      if (path === '/api/orders') { posts.push(init!); return { orderId: 'order-2', status: 'CONFIRMED', totalAmount: 50 } }
      throw new Error(path)
    }) as Api
    render(<EventBrowser api={api} profileKey="buyer-2" onOrder={() => {}} />)
    fireEvent.click(await screen.findByRole('button', { name: 'Concierto' }))
    fireEvent.click(await screen.findByRole('radio', { name: /General/ }))
    expect(screen.getByRole('button', { name: 'Confirmar compra' }).hasAttribute('disabled')).toBe(true)
    fireEvent.click(screen.getByRole('button', { name: 'Entrar o consultar fila' }))
    await waitFor(() => expect(screen.getByRole('button', { name: 'Confirmar compra' }).hasAttribute('disabled')).toBe(false))
    fireEvent.click(screen.getByRole('button', { name: 'Confirmar compra' }))
    await screen.findByText(/Orden order-2: CONFIRMED/)
    expect((posts[0].headers as Record<string, string>)['X-Admission-Token']).toBe('admit-1')
    expect(JSON.parse(String(posts[0].body)).items).toEqual([{ sectorId: 'inventory-general', quantity: 1 }])
  })

  it('consulta la fecha vigente una sola vez por evento y advierte si Catalog falla', async () => {
    const order: Order = { orderId: 'order-1', status: 'CONFIRMED', eventId: 'event-1', eventName: 'Concierto', eventStartsAt: '2029-01-01T20:00:00Z', totalAmount: 100, currency: 'ARS', items: [] }
    const detail = vi.fn()
    const api = vi.fn(async (path: string) => {
      if (path.startsWith('/api/orders?')) return { items: [order, { ...order, orderId: 'order-2' }], page: 0, size: 10, totalElements: 2, totalPages: 1 }
      if (path === '/api/events/event-1') { detail(); throw new Error('404') }
      throw new Error(path)
    }) as Api
    render(<MyTickets api={api} />)
    await waitFor(() => expect(screen.getAllByText(/No se pudo obtener la fecha vigente/)).toHaveLength(2))
    expect(screen.getAllByText(/Fecha histórica al comprar/)).toHaveLength(2)
    expect(detail).toHaveBeenCalledTimes(1)
  })

  it('muestra la fecha posterior de Catalog junto a la histórica de Purchase', async () => {
    const order: Order = { orderId: 'order-3', status: 'CONFIRMED', eventId: 'event-1', eventName: 'Concierto', eventStartsAt: '2029-01-01T20:00:00Z', totalAmount: 100, currency: 'ARS', items: [] }
    const api = vi.fn(async (path: string) => {
      if (path.startsWith('/api/orders?')) return { items: [order], page: 0, size: 10, totalElements: 1, totalPages: 1 }
      if (path === '/api/events/event-1') return event
      throw new Error(path)
    }) as Api
    render(<MyTickets api={api} />)
    await screen.findByText(/Fecha vigente del evento/)
    expect(screen.getByText(/Fecha histórica al comprar/).textContent).not.toEqual(screen.getByText(/Fecha vigente del evento/).textContent)
  })
})
