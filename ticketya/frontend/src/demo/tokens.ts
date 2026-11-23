export type DemoRole = 'CLIENT' | 'ORGANIZER' | 'ADMIN'
export type DemoTokens = Record<DemoRole, string>

const localModules = import.meta.glob<{ default: DemoTokens }>('./tokens.local.ts', { eager: true })
export const demoTokens: DemoTokens = localModules['./tokens.local.ts']?.default ?? {
  CLIENT: '',
  ORGANIZER: '',
  ADMIN: '',
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
