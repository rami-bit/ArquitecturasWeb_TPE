// @vitest-environment jsdom
import { afterEach, describe, expect, it, vi } from 'vitest'
import { cleanup, fireEvent, render, screen, waitFor } from '@testing-library/react'
import { ApiError } from '../api/client'
import { EventBrowser } from './EventBrowser'
import { MyTickets } from './MyTickets'
import { PreferencesView } from './PreferencesView'
import { Api, Order } from './types'

afterEach(() => { cleanup(); sessionStorage.clear() })

const event = { id: 'event-1', name: 'Concierto', eventType: 'MUSIC', startsAt: '2030-01-01T20:00:00Z', highDemand: false, city: 'Rosario', minimumPrice: 100, description: 'Demo', venueName: 'Sala', venueAddress: 'Centro', participants: [], sectors: [{ id: 'catalog-sector', name: 'General', type: 'GENERAL_ADMISSION', price: 100, capacity: 10 }] }
const stock = { eventId: 'event-1', generatedAt: '2029-01-01T00:00:00Z', sectors: [{ sectorId: 'inventory-sector', sourceSectorId: 'catalog-sector', name: 'General', type: 'GENERAL_ADMISSION', available: 10 }] }

async function selectGeneral() {
  fireEvent.click(await screen.findByRole('button', { name: 'Concierto' }))
  fireEvent.click(await screen.findByRole('radio', { name: /General/ }))
}

describe('estados de búsqueda y fila', () => {
  it('muestra página vacía y error del Gateway sin inventar resultados', async () => {
    let invalid = false
    const api = vi.fn(async (path: string) => {
      if (path.startsWith('/api/events?')) {
        if (invalid) throw new ApiError('sort=relevance exige q', 400)
        return { items: [], page: 0, size: 10, totalElements: 0, totalPages: 0 }
      }
      throw new Error(path)
    }) as Api
    render(<EventBrowser api={api} profileKey="" onOrder={() => {}} />)
    await screen.findByText('No hay eventos para estos filtros.')
    invalid = true
    fireEvent.change(screen.getByLabelText('Orden'), { target: { value: 'relevance' } })
    await screen.findByRole('alert')
    expect(screen.getByRole('alert').textContent).toContain('sort=relevance exige q')
    expect(screen.queryByRole('button', { name: 'Concierto' })).toBeNull()
  })

  it('mantiene la compra bloqueada mientras WAITING y cuando la admisión venció', async () => {
    let queueState = { status: 'WAITING', position: 2, peopleAhead: 1, admissionToken: null as string | null, admissionExpiresAt: null as string | null }
    const api = vi.fn(async (path: string) => {
      if (path.startsWith('/api/events?')) return { items: [{ ...event, highDemand: true }], page: 0, size: 10, totalElements: 1, totalPages: 1 }
      if (path === '/api/events/event-1') return { ...event, highDemand: true }
      if (path === '/api/inventory/events/event-1/availability') return stock
      if (path === '/api/queue/events/event-1/entries') return queueState
      throw new Error(path)
    }) as Api
    render(<EventBrowser api={api} profileKey="buyer-queue" onOrder={() => {}} />)
    await selectGeneral()
    fireEvent.click(screen.getByRole('button', { name: 'Entrar o consultar fila' }))
    await screen.findByText(/Estado: WAITING/)
    expect(screen.getByRole('button', { name: 'Confirmar compra' }).hasAttribute('disabled')).toBe(true)
    queueState = { status: 'ADMITTED', position: 2, peopleAhead: 0, admissionToken: 'expired-token', admissionExpiresAt: '2020-01-01T00:00:00Z' }
    fireEvent.click(screen.getByRole('button', { name: 'Entrar o consultar fila' }))
    await screen.findByText(/La admisión venció/)
    expect(screen.getByRole('button', { name: 'Confirmar compra' }).hasAttribute('disabled')).toBe(true)
    expect(api).not.toHaveBeenCalledWith('/api/orders', expect.anything())
  })
})

describe('estados de compra y devolución', () => {
  it('informa REJECTED con el orderId del problema 402 y libera la intención', async () => {
    const api = vi.fn(async (path: string) => {
      if (path.startsWith('/api/events?')) return { items: [event], page: 0, size: 10, totalElements: 1, totalPages: 1 }
      if (path === '/api/events/event-1') return event
      if (path === '/api/inventory/events/event-1/availability') return stock
      if (path === '/api/orders') throw new ApiError('Pago rechazado', 402, undefined, { orderId: 'order-rejected', orderStatus: 'REJECTED' })
      throw new Error(path)
    }) as Api
    render(<EventBrowser api={api} profileKey="buyer-rejected" onOrder={() => {}} />)
    await selectGeneral()
    fireEvent.click(screen.getByRole('button', { name: 'Confirmar compra' }))
    await screen.findByText(/Orden order-rejected: REJECTED/)
    expect(screen.getByText(/no se emitieron entradas/).textContent).toContain('no se emitieron entradas')
    expect(sessionStorage.getItem('ticketya:purchase:buyer-rejected')).toBeNull()
  })

  it('ante 409 refresca disponibilidad y exige revisar la selección', async () => {
    let availabilityReads = 0
    const api = vi.fn(async (path: string) => {
      if (path.startsWith('/api/events?')) return { items: [event], page: 0, size: 10, totalElements: 1, totalPages: 1 }
      if (path === '/api/events/event-1') return event
      if (path === '/api/inventory/events/event-1/availability') {
        availabilityReads++
        return availabilityReads === 1 ? stock : { ...stock, sectors: [{ ...stock.sectors[0], available: 0 }] }
      }
      if (path === '/api/orders') throw new ApiError('Sin cupo', 409)
      throw new Error(path)
    }) as Api
    render(<EventBrowser api={api} profileKey="buyer-conflict" onOrder={() => {}} />)
    await selectGeneral()
    fireEvent.click(screen.getByRole('button', { name: 'Confirmar compra' }))
    await waitFor(() => expect(availabilityReads).toBe(2))
    expect(screen.getByRole('alert').textContent).toContain('Revisá la disponibilidad')
    expect(screen.getByText(/0 disponibles/)).toBeTruthy()
    expect(sessionStorage.getItem('ticketya:purchase:buyer-conflict')).toBeNull()
  })

  it('un rechazo de devolución conserva el ticket y su QR', async () => {
    const order: Order = { orderId: 'order-1', status: 'CONFIRMED', eventId: 'event-1', eventName: 'Concierto', eventStartsAt: '2030-01-01T20:00:00Z', totalAmount: 100, currency: 'ARS', items: [{ sectorName: 'General', quantity: 1, tickets: [{ ticketId: 'ticket-1', status: 'ISSUED', qrPngBase64: 'YWJj' }] }] }
    const api = vi.fn(async (path: string) => {
      if (path.startsWith('/api/orders?')) return { items: [order], page: 0, size: 10, totalElements: 1, totalPages: 1 }
      if (path === '/api/orders/order-1') return order
      if (path === '/api/events/event-1') return event
      if (path === '/api/refunds/tickets/ticket-1') throw new ApiError('Refund window has closed', 409)
      throw new Error(path)
    }) as Api
    render(<MyTickets api={api} />)
    fireEvent.click(await screen.findByRole('button', { name: /Concierto · order-1/ }))
    fireEvent.click(await screen.findByRole('button', { name: 'Solicitar devolución' }))
    await screen.findByRole('alert')
    expect(screen.getByRole('alert').textContent).toContain('Refund window has closed')
    expect(screen.getByRole('img', { name: /QR del ticket ticket-1/ })).toBeTruthy()
    expect(screen.getByText(/Ticket ticket-1/).textContent).toContain('ISSUED')
  })
})

describe('preferencias y recomendaciones', () => {
  it('guarda valores confirmados, muestra vacío y abre una recomendación por eventId', async () => {
    const opened = vi.fn()
    const saved: { body?: Record<string, unknown> } = {}
    let recommendationReads = 0
    const api = vi.fn(async (path: string, init?: RequestInit) => {
      if (path === '/api/customers/me/preferences' && init?.method === 'PUT') {
        saved.body = JSON.parse(String(init.body)) as Record<string, unknown>
        return { ...saved.body, version: 1 }
      }
      if (path === '/api/customers/me/preferences') return { city: null, eventTypes: [], favoriteArtists: [], version: 0 }
      if (path === '/api/customers/me/recommendations?limit=10') {
        recommendationReads++
        return recommendationReads === 1 ? { items: [], warnings: [] } : { items: [{ eventId: 'event-2', name: 'Teatro', city: 'Rosario', startsAt: '2030-02-01T20:00:00Z', minPrice: 80, score: 4, reasons: ['SAME_CITY'] }], warnings: [] }
      }
      throw new Error(path)
    }) as Api
    render(<PreferencesView api={api} openEvent={opened} />)
    await screen.findByText(/versión 0/)
    fireEvent.change(screen.getByLabelText('Ciudad'), { target: { value: 'Rosario' } })
    fireEvent.change(screen.getByLabelText(/Tipos de evento/), { target: { value: 'TEATRO, MUSICA' } })
    fireEvent.click(screen.getByRole('button', { name: 'Guardar preferencias' }))
    await screen.findByText(/versión 1/)
    expect(saved.body).toEqual({ city: 'Rosario', eventTypes: ['TEATRO', 'MUSICA'], favoriteArtists: [] })
    fireEvent.click(screen.getByRole('button', { name: 'Consultar recomendaciones' }))
    await screen.findByText('No hay recomendaciones para mostrar.')
    fireEvent.click(screen.getByRole('button', { name: 'Consultar recomendaciones' }))
    fireEvent.click(await screen.findByRole('button', { name: 'Teatro' }))
    expect(opened).toHaveBeenCalledWith('event-2')
  })
})
