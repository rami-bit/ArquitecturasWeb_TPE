import { beforeEach, expect, it, vi } from 'vitest'
import { requestDemoToken } from './tokens'

const clientId = 'e88d7b2d-9019-4c3c-8bc4-e0ecc43c955b'
const make = (role: string, sub = clientId) => `header.${btoa(JSON.stringify({
  sub, roles: [role], exp: Math.floor(Date.now() / 1000) + 3600,
}))}.signature`

beforeEach(() => vi.restoreAllMocks())

it('solicita un JWT para el perfil fijo y valida identidad y rol', async () => {
  const fetcher = vi.fn().mockResolvedValue(new Response(JSON.stringify({ access_token: make('CLIENT') }), { status: 200 }))
  vi.stubGlobal('fetch', fetcher)
  expect(await requestDemoToken('CLIENT')).toBeTruthy()
  expect(fetcher.mock.calls[0][0]).toContain(`/demo-idp/token?sub=${clientId}&role=CLIENT`)
  fetcher.mockResolvedValueOnce(new Response(JSON.stringify({ access_token: make('ADMIN') }), { status: 200 }))
  await expect(requestDemoToken('CLIENT')).rejects.toThrow('JWT inválido')
})
