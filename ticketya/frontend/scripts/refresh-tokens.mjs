import { writeFile } from 'node:fs/promises'
import { fileURLToPath } from 'node:url'

const endpoint = process.env.IDP_TOKEN_URL || 'http://127.0.0.1:19998/token'
const profiles = {
  CLIENT: 'e88d7b2d-9019-4c3c-8bc4-e0ecc43c955b',
  ORGANIZER: 'e88d7b2d-9019-4c3c-8bc4-e0ecc43c955b',
  ADMIN: 'a1b2c3d4-1111-2222-3333-444455556666',
}
const tokens = {}
for (const [role, sub] of Object.entries(profiles)) {
  const url = new URL(endpoint)
  url.searchParams.set('sub', sub)
  url.searchParams.set('role', role)
  const response = await fetch(url)
  if (!response.ok) throw new Error(`El IdP rechazó ${role}: HTTP ${response.status}`)
  const body = await response.json()
  if (typeof body.access_token !== 'string') throw new Error(`El IdP no devolvió JWT para ${role}`)
  tokens[role] = body.access_token
}
const target = fileURLToPath(new URL('../src/demo/tokens.local.ts', import.meta.url))
await writeFile(target, `// Archivo generado localmente. No versionar.\nexport default ${JSON.stringify(tokens, null, 2)}\n`)
console.log('JWT de prueba renovados para CLIENT, ORGANIZER y ADMIN (sin mostrar valores).')
