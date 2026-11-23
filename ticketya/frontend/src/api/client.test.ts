import { beforeEach, describe, expect, it, vi } from 'vitest'
import { ApiError, apiRequest, setActiveToken, setUnauthorizedHandler } from './client'

function token(role: string, exp = Math.floor(Date.now() / 1000) + 300) {
  return `header.${btoa(JSON.stringify({ sub: 'e88d7b2d-9019-4c3c-8bc4-e0ecc43c955b', exp, roles: [role] }))}.signature`
}

beforeEach(() => {
  vi.restoreAllMocks()
  setActiveToken(null)
  setUnauthorizedHandler(null)
})

describe('apiRequest', () => {
  it('envía el JWT activo al Gateway y limpia identidad al salir', async () => {
    const fetcher = vi.fn().mockImplementation(async () => new Response('{}', { status: 200 }))
    vi.stubGlobal('fetch', fetcher)
    setActiveToken(token('CLIENT'))
    await apiRequest('/api/orders')
    expect(new Headers(fetcher.mock.calls[0][1].headers).get('Authorization')).toMatch(/^Bearer /)
    setActiveToken(null)
    await apiRequest('/api/events')
    expect(new Headers(fetcher.mock.calls[1][1].headers).get('Authorization')).toBeNull()
  })

  it('rechaza el token vencido antes de enviar una escritura', async () => {
    const fetcher = vi.fn()
    vi.stubGlobal('fetch', fetcher)
    const onUnauthorized = vi.fn()
    setUnauthorizedHandler(onUnauthorized)
    setActiveToken(token('CLIENT', Math.floor(Date.now() / 1000) - 1))
    await expect(apiRequest('/api/orders', { method: 'POST', body: '{}' })).rejects.toMatchObject({ status: 401 })
    expect(fetcher).not.toHaveBeenCalled()
    expect(onUnauthorized).toHaveBeenCalledOnce()
  })

  it('interpreta problem+json y preserva la denegación 403', async () => {
    vi.stubGlobal('fetch', vi.fn().mockResolvedValue(new Response(JSON.stringify({ title: 'Forbidden', detail: 'No autorizado' }), {
      status: 403, headers: { 'Content-Type': 'application/problem+json' },
    })))
    setActiveToken(token('ORGANIZER'))
    await expect(apiRequest('/api/venues')).rejects.toMatchObject({ status: 403, message: 'No autorizado' })
  })

  it('invalida la sesión ante 401 sin repetir una escritura', async () => {
    const fetcher = vi.fn().mockResolvedValue(new Response('{}', { status: 401 }))
    vi.stubGlobal('fetch', fetcher)
    const onUnauthorized = vi.fn()
    setUnauthorizedHandler(onUnauthorized)
    setActiveToken(token('CLIENT'))
    await expect(apiRequest('/api/orders', { method: 'POST', body: '{}' })).rejects.toMatchObject({ status: 401 })
    expect(fetcher).toHaveBeenCalledOnce()
    expect(onUnauthorized).toHaveBeenCalledOnce()
  })

  it('rechaza rutas fuera de /api y traduce fallos de red', async () => {
    await expect(apiRequest('http://localhost:8081/internal')).rejects.toBeInstanceOf(ApiError)
    vi.stubGlobal('fetch', vi.fn().mockRejectedValue(new TypeError('offline')))
    await expect(apiRequest('/api/events')).rejects.toMatchObject({ status: 0, message: 'No se pudo conectar con el Gateway.' })
  })
})
