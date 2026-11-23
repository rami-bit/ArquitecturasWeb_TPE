export type Api = <T = unknown>(path: string, init?: RequestInit) => Promise<T>

export interface EventSummary {
  id: string
  name: string
  eventType: string
  startsAt: string
  highDemand: boolean
  city: string
  minimumPrice: number | null
}

export interface Page<T> {
  items: T[]
  page: number
  size: number
  totalElements: number
  totalPages: number
}

export interface EventDetail extends EventSummary {
  description: string
  venueName: string
  venueAddress: string
  participants: string[]
  sectors: { id: string; name: string; type: 'NUMBERED' | 'GENERAL_ADMISSION'; price: number; capacity: number }[]
}

export interface Availability {
  eventId: string
  generatedAt: string
  sectors: {
    sectorId: string
    sourceSectorId: string
    name: string
    type: 'NUMBERED' | 'GENERAL_ADMISSION'
    available: number
    seats?: { seatId: string; row: string; number: number; status: string }[]
  }[]
}

export interface QueueEntry {
  status: 'WAITING' | 'ADMITTED' | 'CONSUMED' | 'EXPIRED' | 'LEFT'
  position: number
  peopleAhead: number
  admissionToken: string | null
  admissionExpiresAt: string | null
}

export interface Order {
  orderId: string
  status: string
  eventId: string
  eventName: string
  eventStartsAt: string
  totalAmount: number
  currency: string
  items: {
    sectorName: string
    quantity: number
    tickets: { ticketId: string; status: string; qrPngBase64: string; row?: string | null; seatNumber?: number | null }[]
  }[]
}

export interface Refund {
  id: string
  status: string
  progress: string
  decision: string | null
  ticketId: string
}

export interface Preferences {
  city: string | null
  eventTypes: string[]
  favoriteArtists: string[]
  version: number
}

export interface Recommendation {
  eventId: string
  name: string
  startsAt: string
  city: string
  minPrice: number
  score: number
  reasons: string[]
}

export interface Intent {
  key: string
  body: string
  admissionToken?: string
  orderId?: string
}

export function errorMessage(error: unknown): string {
  if (error instanceof Error) return error.message
  return String(error)
}

export function errorStatus(error: unknown): number | undefined {
  return typeof error === 'object' && error !== null && 'status' in error
    ? Number((error as { status: unknown }).status)
    : undefined
}

export function formatDate(value: string): string {
  return new Date(value).toLocaleString('es-AR', { dateStyle: 'medium', timeStyle: 'short' })
}

export function formatMoney(value: number): string {
  return new Intl.NumberFormat('es-AR', { style: 'currency', currency: 'ARS' }).format(value)
}

export function buildOrderBody(eventId: string, sector: Availability['sectors'][number], seatIds: string[], quantity: number, paymentToken: string): string {
  const item = sector.type === 'NUMBERED'
    ? { sectorId: sector.sectorId, seatIds }
    : { sectorId: sector.sectorId, quantity }
  return JSON.stringify({ eventId, items: [item], payment: { method: 'CARD', token: paymentToken } })
}
