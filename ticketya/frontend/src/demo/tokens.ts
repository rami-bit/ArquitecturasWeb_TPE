export type DemoRole = 'CLIENT' | 'ORGANIZER' | 'ADMIN'
const profileIds: Record<DemoRole, string> = {
  CLIENT: 'e88d7b2d-9019-4c3c-8bc4-e0ecc43c955b',
  ORGANIZER: 'e88d7b2d-9019-4c3c-8bc4-e0ecc43c955b',
  ADMIN: 'a1b2c3d4-1111-2222-3333-444455556666',
}

export async function requestDemoToken(role: DemoRole): Promise<string> {
  const query = new URLSearchParams({ sub: profileIds[role], role })
  const response = await fetch(`/demo-idp/token?${query}`, { cache: 'no-store' })
  if (!response.ok) throw new Error(`El IdP de prueba respondió HTTP ${response.status}.`)
  const body: unknown = await response.json()
  const token = body && typeof body === 'object' && 'access_token' in body ? body.access_token : null
  if (typeof token !== 'string' || !tokenValidForRole(token, role) || decodeJwt(token)?.sub !== profileIds[role]) {
    throw new Error('El IdP de prueba devolvió un JWT inválido para este perfil.')
  }
  return token
}

export function decodeJwt(token: string): { sub: string; exp: number; roles: string[] } | null {
  try {
    const payload = token.split('.')[1]
    if (!payload) return null
    const normalized = payload.replace(/-/g, '+').replace(/_/g, '/')
    const claims = JSON.parse(atob(normalized)) as Record<string, unknown>
    if (typeof claims.sub !== 'string' || typeof claims.exp !== 'number' || !Array.isArray(claims.roles)) return null
    return { sub: claims.sub, exp: claims.exp, roles: claims.roles.filter((role): role is string => typeof role === 'string') }
  } catch {
    return null
  }
}

export function tokenValidForRole(token: string, role: DemoRole): boolean {
  const claims = decodeJwt(token)
  return !!claims && claims.exp * 1000 > Date.now() && claims.roles.length === 1 && claims.roles[0] === role
}
