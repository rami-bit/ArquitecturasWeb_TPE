// @vitest-environment jsdom
import { afterEach, beforeEach, expect, it, vi } from 'vitest'
import { cleanup, render, screen, waitFor } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import App from './App'
import { setActiveToken } from './api/client'
import { requestDemoToken } from './demo/tokens'

vi.mock('./demo/tokens', async (importOriginal) => {
  const actual = await importOriginal<typeof import('./demo/tokens')>()
  const make = (role: string) => `header.${btoa(JSON.stringify({
    sub: role === 'ADMIN' ? 'a1b2c3d4-1111-2222-3333-444455556666' : 'e88d7b2d-9019-4c3c-8bc4-e0ecc43c955b',
    roles: [role], exp: Math.floor(Date.now() / 1000) + 3600,
  }))}.signature`
  return { ...actual, requestDemoToken: vi.fn(async (role: string) => make(role)) }
})

vi.mock('./customer/CustomerApp', () => ({
  CustomerApp: ({ api }: { api: (path: string) => Promise<unknown> }) =>
    <button onClick={() => void api('/api/events')}>Consultar eventos</button>,
}))
vi.mock('./operations/OperationsArea', () => ({
  default: ({ role }: { role: string }) => <p>Vista {role}</p>,
}))
vi.mock('./notifications/NotificationsArea', () => ({ NotificationsArea: () => <p>Email</p> }))

beforeEach(() => {
  vi.mocked(requestDemoToken).mockClear()
  setActiveToken(null)
  vi.stubGlobal('fetch', vi.fn().mockImplementation(async () => new Response('{}', { status: 200 })))
})
afterEach(() => cleanup())

it('renueva el JWT del perfil activo antes de vencer', async () => {
  const user = userEvent.setup()
  const expiring = `header.${btoa(JSON.stringify({ sub: 'e88d7b2d-9019-4c3c-8bc4-e0ecc43c955b', roles: ['CLIENT'], exp: Math.floor(Date.now() / 1000) + 301 }))}.signature`
  const renewed = `header.${btoa(JSON.stringify({ sub: 'e88d7b2d-9019-4c3c-8bc4-e0ecc43c955b', roles: ['CLIENT'], exp: Math.floor(Date.now() / 1000) + 3600 }))}.signature`
  vi.mocked(requestDemoToken).mockResolvedValueOnce(expiring).mockResolvedValueOnce(renewed)
  render(<App />)
  await user.selectOptions(screen.getByLabelText('Perfil'), 'CLIENT')
  await waitFor(() => expect(requestDemoToken).toHaveBeenCalledTimes(2), { timeout: 2500 })
  expect(screen.getByText(/Token de prueba válido hasta/)).toBeTruthy()
  await user.click(screen.getByRole('button', { name: 'Consultar eventos' }))
  expect(new Headers(vi.mocked(fetch).mock.calls[0][1]?.headers).get('Authorization')).toBe(`Bearer ${renewed}`)
})

it('alterna perfil y Sale sin enviar el JWT anterior', async () => {
  const user = userEvent.setup()
  render(<App />)
  await user.click(screen.getByRole('button', { name: 'Consultar eventos' }))
  await user.selectOptions(screen.getByLabelText('Perfil'), 'CLIENT')
  await screen.findByRole('button', { name: 'Salir' })
  await user.click(screen.getByRole('button', { name: 'Consultar eventos' }))
  await user.click(screen.getByRole('button', { name: 'Salir' }))
  await user.click(screen.getByRole('button', { name: 'Consultar eventos' }))
  await waitFor(() => expect(vi.mocked(fetch)).toHaveBeenCalledTimes(3))
  const calls = vi.mocked(fetch).mock.calls
  expect(new Headers(calls[0][1]?.headers).get('Authorization')).toBeNull()
  expect(new Headers(calls[1][1]?.headers).get('Authorization')).toMatch(/^Bearer /)
  expect(new Headers(calls[2][1]?.headers).get('Authorization')).toBeNull()
})

it('muestra navegación de gestión sólo a perfiles permitidos', async () => {
  const user = userEvent.setup()
  render(<App />)
  expect(screen.queryByRole('button', { name: 'Gestión de eventos' })).toBeNull()
  await user.selectOptions(screen.getByLabelText('Perfil'), 'ORGANIZER')
  expect(await screen.findByText('Vista ORGANIZER')).toBeTruthy()
  await user.selectOptions(screen.getByLabelText('Perfil'), 'ADMIN')
  expect(await screen.findByText('Vista ADMIN')).toBeTruthy()
})
