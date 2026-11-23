import { apiRequest } from '../api/client'

export type Role = 'ORGANIZER' | 'ADMIN'
export type EventStatus = 'DRAFT' | 'PUBLISHED' | 'SUSPENDED' | 'SOLD_OUT' | 'CANCELLED' | 'FINISHED'
export type Offering = { sectorId: string; price: number }
export type ManagedEvent = {
  id: string; name: string; description: string; eventType: string; startsAt: string
  organizerId: string; venueId: string; status: EventStatus; highDemand: boolean
  scheduleRevision: number; participants: string[]; offerings: Offering[]
}
export type Seat = { rowLabel: string; seatNumber: number }
export type VenueSector = { id: string; name: string; type: 'NUMBERED' | 'GENERAL_ADMISSION'; capacity: number; seats: Seat[] }
export type Venue = {
  id: string; name: string; address: string; city: string; latitude: number; longitude: number
  sectors: VenueSector[]
}
export type VenueInput = Omit<Venue, 'id' | 'sectors'> & {
  sectors: Array<Omit<VenueSector, 'id'>>
}
export type EventInput = {
  name: string; description: string; eventType: string; startsAt: string; venueId: string
  highDemand: boolean; participants: string[]; offerings: Offering[]; organizerId?: string
}
export type ReportRow = {
  sourceSectorId: string; sectorName: string | null; sectorType: string | null
  capacity: number | null; soldTickets: number; refundedTickets: number; activeTickets: number
  occupancyPercent: number | null; grossRevenue: number; refundedAmount: number; netRevenue: number
}
export type EventReport = {
  eventId: string; currency: string; capacityAvailable: boolean; sectors: ReportRow[]
  totals: Omit<ReportRow, 'sourceSectorId' | 'sectorName' | 'sectorType'>; warnings: string[]
}
export type CancellationProgress = {
  eventId: string; status: string; totalTickets: number; completedTickets: number; pendingTickets: number
}
export type RefundDecision = {
  id: string; status: string; decision: string | null; progress: string; amount: number
}
export type AuditEvent = {
  eventId: string; correlationId: string; service: string; operation: string; outcome: string
  actorId: string | null; resourceType: string | null; resourceId: string | null; occurredAt: string
}
export type AuditPage = { items: AuditEvent[]; page: number; size: number; totalItems: number }
export type AuditTrace = { correlationId: string; eventCount: number; services: string[]; events: AuditEvent[] }
export type ScheduleResult = {
  eventId: string; changeId: string; previousStartsAt: string; startsAt: string; scheduleRevision: number
}

const json = (method: string, body: unknown, headers?: HeadersInit): RequestInit => ({
  method, headers: { 'Content-Type': 'application/json', ...headers }, body: JSON.stringify(body),
})
const id = (value: string) => encodeURIComponent(value)

export const operationsApi = {
  events: () => apiRequest<ManagedEvent[]>('/api/management/events'),
  event: (eventId: string) => apiRequest<ManagedEvent>(`/api/management/events/${id(eventId)}`),
  createEvent: (body: EventInput) => apiRequest<ManagedEvent>('/api/management/events', json('POST', body)),
  updateEvent: (eventId: string, body: EventInput) =>
    apiRequest<ManagedEvent>(`/api/management/events/${id(eventId)}`, json('PUT', body)),
  offerings: (eventId: string, offerings: Offering[]) =>
    apiRequest<ManagedEvent>(`/api/management/events/${id(eventId)}/offerings`, json('PUT', { offerings })),
  publish: (eventId: string) => apiRequest<ManagedEvent>(`/api/management/events/${id(eventId)}/publish`, { method: 'POST' }),
  suspend: (eventId: string) => apiRequest<ManagedEvent>(`/api/management/events/${id(eventId)}/suspend`, { method: 'POST' }),
  resume: (eventId: string) => apiRequest<ManagedEvent>(`/api/management/events/${id(eventId)}/resume`, { method: 'POST' }),
  cancel: (eventId: string) => apiRequest<CancellationProgress>(`/api/events/${id(eventId)}/cancellation`, { method: 'POST' }),
  cancellation: (eventId: string) => apiRequest<CancellationProgress>(`/api/events/${id(eventId)}/cancellation`),
  report: (eventId: string) => apiRequest<EventReport>(`/api/reports/events/${id(eventId)}`),
  venues: (page = 0) => apiRequest<{ content: Venue[]; page: number; totalPages: number }>(`/api/venues?page=${page}&size=20`),
  async allVenues() {
    const first = await this.venues(0)
    const rest = await Promise.all(Array.from({ length: Math.max(0, first.totalPages - 1) }, (_, index) => this.venues(index + 1)))
    return [first, ...rest].flatMap(page => page.content)
  },
  createVenue: (body: VenueInput) => apiRequest<Venue>('/api/venues', json('POST', body)),
  updateVenue: (venueId: string, body: VenueInput) => apiRequest<Venue>(`/api/venues/${id(venueId)}`, json('PUT', body)),
  decideRefund: (refundId: string, decision: 'APPROVED' | 'REJECTED') =>
    apiRequest<RefundDecision>(`/api/refunds/${id(refundId)}/decision`, json('POST', { decision })),
  audit: (params: URLSearchParams) => apiRequest<AuditPage>(`/api/audit/events?${params}`),
  trace: (correlationId: string) => apiRequest<AuditTrace>(`/api/audit/traces/${id(correlationId)}`),
  postpone: (eventId: string, startsAt: string, expectedScheduleRevision: number, key: string) =>
    apiRequest<ScheduleResult>(`/api/management/events/${id(eventId)}/schedule`,
      json('PATCH', { startsAt, expectedScheduleRevision }, { 'Idempotency-Key': key })),
}

export function scheduleValidation(newDate: string, currentDate: string, now = Date.now()): string | null {
  const proposed = Date.parse(newDate)
  const current = Date.parse(currentDate)
  if (!Number.isFinite(proposed) || !Number.isFinite(current)) return 'Ingresá una fecha válida.'
  if (proposed <= current) return 'La nueva fecha debe ser posterior a la vigente.'
  if (proposed <= now + 72 * 60 * 60 * 1000) return 'La nueva fecha debe superar estrictamente las 72 horas desde ahora.'
  return null
}
