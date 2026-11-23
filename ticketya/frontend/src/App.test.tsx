// @vitest-environment jsdom
import { beforeEach, expect, it, vi } from 'vitest'
import { render, screen, waitFor } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import App from './App'
import { setActiveToken } from './api/client'

vi.mock('./demo/tokens', async (importOriginal) => {
  const actual = await importOriginal<typeof import('./demo/tokens')>()
  const make = (role: string) => `header.${btoa(JSON.stringify({
    sub: role === 'ADMIN' ? 'a1b2c3d4-1111-2222-3333-444455556666' : 'e88d7b2d-9019-4c3c-8bc4-e0ecc43c955b',
    roles: [role], exp: Math.floor(Date.now() / 1000) + 300,
  }))}.signature`
  return { ...actual, demoTokens: { CLIENT: make('CLIENT'), ORGANIZER: make('ORGANIZER'), ADMIN: make('ADMIN') } }
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
  setActiveToken(null)
  vi.stubGlobal('fetch', vi.fn().mockImplementation(async () => new Response('{}', { status: 200 })))
})

it('alterna perfil y Sale sin enviar el JWT anterior', async () => {
  const user = userEvent.setup()
  render(<App />)
  await user.click(screen.getByRole('button', { name: 'Consultar eventos' }))
  await user.selectOptions(screen.getByLabelText('Perfil'), 'CLIENT')
  await user.click(screen.getByRole('button', { name: 'Consultar eventos' }))
  await user.click(screen.getByRole('button', { name: 'Salir' }))
  await user.click(screen.getByRole('button', { name: 'Consultar eventos' }))
  const calls = vi.mocked(fetch).mock.calls
  await waitFor(() => expect(calls).toHaveLength(3))
  expect(new Headers(calls[0][1]?.headers).get('Authorization')).toBeNull()
  expect(new Headers(calls[1][1]?.headers).get('Authorization')).toMatch(/^Bearer /)
  expect(new Headers(calls[2][1]?.headers).get('Authorization')).toBeNull()
})

it('muestra navegación de gestión sólo a perfiles permitidos', async () => {
  const user = userEvent.setup()
  render(<App />)
  expect(screen.queryByRole('button', { name: 'Gestión de eventos' })).toBeNull()
  await user.selectOptions(screen.getByLabelText('Perfil'), 'ORGANIZER')
  expect(screen.getByText('Vista ORGANIZER')).toBeTruthy()
  await user.selectOptions(screen.getByLabelText('Perfil'), 'ADMIN')
  expect(screen.getByText('Vista ADMIN')).toBeTruthy()
})
