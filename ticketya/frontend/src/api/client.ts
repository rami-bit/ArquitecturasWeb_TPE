import { decodeJwt } from '../demo/tokens'

export class ApiError extends Error {
  constructor(message: string, public readonly status: number, public readonly detail?: string, public readonly problem?: Record<string, unknown>) {
    super(message)
    this.name = 'ApiError'
  }
}

let activeToken: string | null = null
let sessionAbort = new AbortController()
let unauthorizedHandler: (() => void) | null = null

export function setActiveToken(token: string | null): void {
  sessionAbort.abort()
  sessionAbort = new AbortController()
  activeToken = token
}

export function setUnauthorizedHandler(handler: (() => void) | null): void {
  unauthorizedHandler = handler
}

export async function apiRequest<T = unknown>(path: string, init: RequestInit = {}): Promise<T> {
  if (!path.startsWith('/api/') || path.startsWith('//')) throw new ApiError('Ruta fuera del Gateway', 0)
  const token = activeToken
  if (token) {
    const claims = decodeJwt(token)
    if (!claims || claims.exp * 1000 <= Date.now()) {
      setActiveToken(null)
      unauthorizedHandler?.()
      throw new ApiError('Token de prueba vencido. Renovalo para continuar.', 401)
    }
  }

  const headers = new Headers(init.headers)
  headers.delete('Authorization')
  headers.delete('X-User-Id')
  headers.delete('X-User-Role')
  if (token) headers.set('Authorization', `Bearer ${token}`)
  if (init.body && typeof init.body === 'string' && !headers.has('Content-Type')) headers.set('Content-Type', 'application/json')
  headers.set('Accept', 'application/json, application/problem+json')

  const session = sessionAbort
  const abort = new AbortController()
  const cancel = () => abort.abort()
  session.signal.addEventListener('abort', cancel, { once: true })
  init.signal?.addEventListener('abort', cancel, { once: true })
  if (session.signal.aborted || init.signal?.aborted) abort.abort()
  try {
    let response: Response
    try {
      response = await fetch(path, { ...init, headers, signal: abort.signal })
    } catch (error) {
      if (error instanceof DOMException && error.name === 'AbortError') throw error
      throw new ApiError('No se pudo conectar con el Gateway.', 0)
    }
    const raw = await response.text()
    let body: unknown = undefined
    if (raw) {
      try { body = JSON.parse(raw) } catch { body = raw }
    }
    if (!response.ok) {
      const problem = body && typeof body === 'object' ? body as Record<string, unknown> : {}
      const detail = typeof problem.detail === 'string' ? problem.detail : undefined
      const title = typeof problem.title === 'string' ? problem.title : undefined
      if (response.status === 401 && token === activeToken) {
        setActiveToken(null)
        unauthorizedHandler?.()
      }
      const message = response.status === 403 ? (detail ?? 'Este perfil no tiene permiso para esta operación.') :
        response.status === 401 ? 'La sesión de prueba dejó de ser válida. Renová los tokens.' :
        (detail ?? title ?? `Error HTTP ${response.status}`)
      throw new ApiError(message, response.status, detail, problem)
    }
    return body as T
  } finally {
    session.signal.removeEventListener('abort', cancel)
    init.signal?.removeEventListener('abort', cancel)
  }
}
