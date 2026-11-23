// @vitest-environment jsdom
import { afterEach, describe, expect, it, vi } from 'vitest'
import { cleanup, fireEvent, render, screen, waitFor } from '@testing-library/react'
import { apiRequest } from '../api/client'
import OperationsArea from './OperationsArea'
import { operationsApi, scheduleValidation } from './api'

vi.mock('../api/client', () => ({ apiRequest: vi.fn() }))
const request = vi.mocked(apiRequest)
afterEach(() => { cleanup(); vi.resetAllMocks(); vi.restoreAllMocks() })

const event = {
  id: 'event-1', name: 'Concierto', description: 'Evento', eventType: 'MUSIC',
  startsAt: '2030-01-01T20:00:00Z', organizerId: 'owner-1', venueId: 'venue-1',
  status: 'PUBLISHED', highDemand: false, scheduleRevision: 2,
  participants: ['Banda'], offerings: [{ sectorId: 'sector-1', price: 1000 }],
}
const venue = {
  id: 'venue-1', name: 'Estadio', address: 'Calle 1', city: 'Buenos Aires',
  latitude: -34, longitude: -58,
  sectors: [{ id: 'sector-1', name: 'Campo', type: 'GENERAL_ADMISSION', capacity: 100, seats: [] }],
}

describe('operaciones por Gateway', () => {
  it('crea borrador con oferta y publica sólo tras confirmar', async () => {
    const draft = { ...event, status: 'DRAFT', scheduleRevision: 0 }
    let saved = false
    request.mockImplementation(async (path, init) => {
      if (path === '/api/venues?page=0&size=20') return { content: [venue], page: 0, totalPages: 1 } as never
      if (path === '/api/management/events' && !init?.method) return (saved ? [draft] : []) as never
      if (path === '/api/management/events' && init?.method === 'POST') {
        const body = JSON.parse(String(init.body))
        expect(body).toMatchObject({
          name: 'Concierto', venueId: 'venue-1', participants: ['Banda'],
          offerings: [{ sectorId: 'sector-1', price: 1500 }], highDemand: true,
        })
        saved = true
        return draft as never
      }
      if (path === '/api/management/events/event-1') return draft as never
      if (path === '/api/management/events/event-1/publish') return { ...draft, status: 'PUBLISHED' } as never
      throw new Error(`Unexpected ${path} ${init?.method}`)
    })
    vi.spyOn(window, 'confirm').mockReturnValue(true)
    render(<OperationsArea role="ORGANIZER" userId="owner-1" />)
    await screen.findByRole('option', { name: 'Estadio · Buenos Aires' })
    fireEvent.change(screen.getByLabelText('Nombre'), { target: { value: 'Concierto' } })
    fireEvent.change(screen.getByLabelText('Descripción'), { target: { value: 'Evento' } })
    fireEvent.change(screen.getByLabelText('Fecha y hora'), { target: { value: '2030-01-01T20:00' } })
    fireEvent.change(screen.getByLabelText('Sede'), { target: { value: 'venue-1' } })
    fireEvent.change(screen.getByLabelText('Participantes (separados por coma)'), { target: { value: 'Banda' } })
    fireEvent.click(screen.getByLabelText(/Alta demanda/))
    fireEvent.click(screen.getByLabelText(/Campo/))
    fireEvent.change(screen.getByLabelText('Precio ARS'), { target: { value: '1500' } })
    fireEvent.click(screen.getByRole('button', { name: 'Guardar borrador' }))
    await screen.findByText(/Borrador guardado/)
    fireEvent.click(screen.getByRole('button', { name: 'Publicar' }))
    await screen.findByText(/Evento publicado/)
    expect(request).toHaveBeenCalledWith('/api/management/events/event-1/publish', { method: 'POST' })
    expect(window.confirm).toHaveBeenCalledTimes(1)
  })

  it('edita un borrador con ruta separada para offerings', async () => {
    const draft = { ...event, status: 'DRAFT' }
    request.mockImplementation(async (path, init) => {
      if (path === '/api/management/events' && !init?.method) return [draft] as never
      if (path === '/api/venues?page=0&size=20') return { content: [venue], page: 0, totalPages: 1 } as never
      if (path === '/api/management/events/event-1' && init?.method === 'PUT') return draft as never
      if (path === '/api/management/events/event-1/offerings') return draft as never
      if (path === '/api/management/events/event-1' && !init?.method) return draft as never
      throw new Error(`Unexpected ${path} ${init?.method}`)
    })
    render(<OperationsArea role="ORGANIZER" userId="owner-1" />)
    fireEvent.click(await screen.findByRole('button', { name: 'Concierto' }))
    fireEvent.change(screen.getByLabelText('Descripción'), { target: { value: 'Descripción editada' } })
    fireEvent.click(screen.getByRole('button', { name: 'Guardar borrador' }))
    await screen.findByText(/Borrador guardado/)
    expect(request).toHaveBeenCalledWith('/api/management/events/event-1', expect.objectContaining({ method: 'PUT' }))
    expect(request).toHaveBeenCalledWith('/api/management/events/event-1/offerings', expect.objectContaining({
      method: 'PUT', body: JSON.stringify({ offerings: draft.offerings }),
    }))
  })

  it('usa Purchase para cancelar, Catalog para postergar y revisión más clave idempotente', async () => {
    request.mockImplementation(async (path, init) => {
      if (path === '/api/management/events') return [event] as never
      if (path === '/api/venues?page=0&size=20') return { content: [venue], page: 0, totalPages: 1 } as never
      if (path === '/api/management/events/event-1') return event as never
      if (path === '/api/management/events/event-1/schedule') return {
        eventId: event.id, changeId: 'change-1', previousStartsAt: event.startsAt,
        startsAt: '2030-02-01T20:00:00Z', scheduleRevision: 3,
      } as never
      if (path === '/api/events/event-1/cancellation') return {
        eventId: event.id, status: 'PROCESSING', totalTickets: 2, completedTickets: 1, pendingTickets: 1,
      } as never
      throw new Error(`Unexpected ${path} ${init?.method}`)
    })
    vi.spyOn(window, 'confirm').mockReturnValue(true)
    render(<OperationsArea role="ADMIN" userId="admin-1" />)
    fireEvent.click(await screen.findByRole('button', { name: 'Concierto' }))
    fireEvent.click(screen.getByRole('button', { name: 'Cancelar e iniciar reintegros' }))
    await screen.findByText(/Reintegrados 1 de 2/)
    expect(request).toHaveBeenCalledWith('/api/events/event-1/cancellation', { method: 'POST' })
    fireEvent.change(screen.getByLabelText('Nueva fecha y hora'), { target: { value: '2030-02-01T20:00' } })
    fireEvent.click(screen.getByRole('button', { name: 'Confirmar postergación' }))
    await waitFor(() => expect(request).toHaveBeenCalledWith(
      '/api/management/events/event-1/schedule',
      expect.objectContaining({
        method: 'PATCH',
        body: JSON.stringify({ startsAt: new Date('2030-02-01T20:00').toISOString(), expectedScheduleRevision: 2 }),
        headers: expect.objectContaining({ 'Idempotency-Key': expect.any(String) }),
      }),
    ))
  })

  it('oculta las herramientas de ADMIN al organizador y marca capacidad desconocida en el reporte', async () => {
    request.mockImplementation(async path => {
      if (path === '/api/management/events') return [event] as never
      if (path === '/api/venues?page=0&size=20') return { content: [venue], page: 0, totalPages: 1 } as never
      if (path === '/api/reports/events/event-1') return {
        eventId: event.id, currency: 'ARS', capacityAvailable: false,
        sectors: [{ sourceSectorId: 'sector-1', sectorName: 'Campo', sectorType: 'GENERAL_ADMISSION',
          capacity: null, soldTickets: 2, refundedTickets: 1, activeTickets: 1, occupancyPercent: null,
          grossRevenue: 2000, refundedAmount: 1000, netRevenue: 1000 }],
        totals: { capacity: null, soldTickets: 2, refundedTickets: 1, activeTickets: 1,
          occupancyPercent: null, grossRevenue: 2000, refundedAmount: 1000, netRevenue: 1000 },
        warnings: ['Inventory no disponible'],
      } as never
      throw new Error(`Unexpected ${path}`)
    })
    render(<OperationsArea role="ORGANIZER" userId="owner-1" />)
    expect(screen.queryByRole('button', { name: 'Sedes' })).toBeNull()
    expect(screen.queryByRole('button', { name: 'Cancelar e iniciar reintegros' })).toBeNull()
    fireEvent.click(await screen.findByRole('button', { name: 'Concierto' }))
    fireEvent.click(screen.getByRole('button', { name: 'Ver reporte' }))
    expect(await screen.findByText(/Capacidad y ocupación no disponibles/)).toBeTruthy()
    expect(screen.getAllByText('—').length).toBeGreaterThan(0)
  })

  it('valida postergación estrictamente posterior a la fecha vigente y ahora + 72h', () => {
    const now = Date.parse('2026-11-23T12:00:00Z')
    expect(scheduleValidation('2026-11-26T12:00:00Z', '2026-11-24T12:00:00Z', now)).toMatch(/72 horas/)
    expect(scheduleValidation('2026-11-27T12:00:00Z', '2026-11-27T12:00:00Z', now)).toMatch(/posterior/)
    expect(scheduleValidation('2026-11-28T12:00:00Z', '2026-11-27T12:00:00Z', now)).toBeNull()
  })

  it('envía decisión por ID conocido sin consulta previa', async () => {
    request.mockResolvedValue({ id: 'refund-1', status: 'COMPLETED', decision: 'APPROVED', progress: 'COMPLETED', amount: 100 } as never)
    await operationsApi.decideRefund('refund-1', 'APPROVED')
    expect(request).toHaveBeenCalledWith('/api/refunds/refund-1/decision', expect.objectContaining({
      method: 'POST', body: JSON.stringify({ decision: 'APPROVED' }),
    }))
    expect(request).toHaveBeenCalledTimes(1)
  })

  it('crea sede numerada y muestra error de devolución inválida sin afirmar éxito', async () => {
    request.mockImplementation(async (path, init) => {
      if (path === '/api/management/events') return [] as never
      if (path === '/api/venues?page=0&size=20' && !init?.method) return { content: [], page: 0, totalPages: 0 } as never
      if (path === '/api/venues' && init?.method === 'POST') return {
        ...venue, sectors: [{ id: 'seat-sector', name: 'Platea', type: 'NUMBERED', capacity: 2,
          seats: [{ rowLabel: 'A', seatNumber: 1 }, { rowLabel: 'A', seatNumber: 2 }] }],
      } as never
      if (path === '/api/refunds/bad-id/decision') throw new Error('Refund not found')
      throw new Error(`Unexpected ${path} ${init?.method}`)
    })
    vi.spyOn(window, 'confirm').mockReturnValue(true)
    render(<OperationsArea role="ADMIN" userId="admin-1" />)
    fireEvent.click(screen.getByRole('button', { name: 'Sedes' }))
    fireEvent.change(screen.getByLabelText('Nombre'), { target: { value: 'Estadio' } })
    fireEvent.change(screen.getByLabelText('Dirección'), { target: { value: 'Calle 1' } })
    fireEvent.change(screen.getByLabelText('Ciudad'), { target: { value: 'Buenos Aires' } })
    fireEvent.click(screen.getByRole('button', { name: 'Agregar sector' }))
    fireEvent.change(screen.getAllByLabelText('Nombre')[1], { target: { value: 'Platea' } })
    fireEvent.change(screen.getByLabelText('Tipo'), { target: { value: 'NUMBERED' } })
    fireEvent.change(screen.getByLabelText('Capacidad'), { target: { value: '2' } })
    fireEvent.change(screen.getByLabelText('Asientos (A:1, A:2)'), { target: { value: 'A:1, A:2' } })
    fireEvent.click(screen.getByRole('button', { name: 'Guardar sede' }))
    await screen.findByText(/Sede guardada/)
    expect(request).toHaveBeenCalledWith('/api/venues', expect.objectContaining({
      method: 'POST', body: expect.stringContaining('"rowLabel":"A","seatNumber":2'),
    }))
    fireEvent.click(screen.getByRole('button', { name: 'Devoluciones' }))
    fireEvent.change(screen.getByLabelText('UUID de solicitud'), { target: { value: 'bad-id' } })
    fireEvent.click(screen.getByRole('button', { name: 'Confirmar decisión' }))
    expect(await screen.findByRole('alert')).toHaveProperty('textContent', 'Refund not found')
    expect(screen.queryByText(/Solicitud bad-id:/)).toBeNull()
  })

  it('envía filtros admitidos y abre la traza sin exponer detalles sensibles', async () => {
    request.mockImplementation(async path => {
      if (path === '/api/management/events') return [] as never
      if (path === '/api/venues?page=0&size=20') return { content: [], page: 0, totalPages: 0 } as never
      if (path.startsWith('/api/audit/events?')) return {
        items: [{ eventId: 'audit-1', correlationId: 'trace-1', service: 'purchase-service',
          operation: 'PURCHASE_ORDER', outcome: 'CONFIRMED', actorId: 'client-1',
          resourceType: 'ORDER', resourceId: 'order-1', occurredAt: '2030-01-01T20:00:00Z',
          details: { token: 'secret' } }],
        page: 0, size: 20, totalItems: 1,
      } as never
      if (path === '/api/audit/traces/trace-1') return {
        correlationId: 'trace-1', eventCount: 1, services: ['purchase-service'], events: [],
      } as never
      throw new Error(`Unexpected ${path}`)
    })
    render(<OperationsArea role="ADMIN" userId="admin-1" />)
    fireEvent.click(screen.getByRole('button', { name: 'Auditoría' }))
    fireEvent.change(screen.getByLabelText('service'), { target: { value: 'purchase-service' } })
    fireEvent.click(screen.getByRole('button', { name: 'Buscar' }))
    const traceButton = await screen.findByRole('button', { name: 'trace-1' })
    expect(request).toHaveBeenCalledWith('/api/audit/events?page=0&size=20&service=purchase-service')
    expect(screen.queryByText('secret')).toBeNull()
    fireEvent.click(traceButton)
    await screen.findByText(/1 eventos/)
    expect(request).toHaveBeenCalledWith('/api/audit/traces/trace-1')
  })

  it('ante conflicto de revisión o transición refresca el evento y conserva el error', async () => {
    let revision = 2
    request.mockImplementation(async (path, init) => {
      if (path === '/api/management/events') return [event] as never
      if (path === '/api/venues?page=0&size=20') return { content: [venue], page: 0, totalPages: 1 } as never
      if (path === '/api/management/events/event-1' && !init?.method) return { ...event, scheduleRevision: revision } as never
      if (path === '/api/management/events/event-1/schedule') { revision = 3; throw new Error('Revision conflict') }
      if (path === '/api/management/events/event-1/suspend') throw new Error('Invalid state')
      throw new Error(`Unexpected ${path} ${init?.method}`)
    })
    vi.spyOn(window, 'confirm').mockReturnValue(true)
    render(<OperationsArea role="ADMIN" userId="admin-1" />)
    fireEvent.click(await screen.findByRole('button', { name: 'Concierto' }))
    fireEvent.change(screen.getByLabelText('Nueva fecha y hora'), { target: { value: '2030-02-01T20:00' } })
    fireEvent.click(screen.getByRole('button', { name: 'Confirmar postergación' }))
    await screen.findByText('Revision conflict')
    await screen.findByText(/Revisión: 3/)
    fireEvent.click(screen.getByRole('button', { name: 'Suspender' }))
    await screen.findByText('Invalid state')
    expect(request).toHaveBeenCalledWith('/api/management/events/event-1')
  })
})
