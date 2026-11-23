// Smoke de navegación real con Chrome/Edge instalado; no descarga navegadores.
import { spawn } from 'node:child_process'
import { mkdtemp, rm } from 'node:fs/promises'
import { tmpdir } from 'node:os'
import { join, resolve } from 'node:path'
import net from 'node:net'
import WebSocket from 'ws'

const browser = process.env.CHROME_PATH || 'C:\\Program Files\\Google\\Chrome\\Application\\chrome.exe'
const app = process.env.FRONTEND_URL || 'http://127.0.0.1:5173/'
const profile = await mkdtemp(join(tmpdir(), 'ticketya-browser-smoke-'))
const port = await new Promise((done) => {
  const server = net.createServer()
  server.listen(0, '127.0.0.1', () => {
    const port = server.address().port
    server.close(() => done(port))
  })
})
const chrome = spawn(browser, [
  '--headless=new', '--no-first-run', '--no-default-browser-check', '--remote-allow-origins=*',
  '--disable-gpu', '--disable-gpu-sandbox', '--disable-features=VizDisplayCompositor',
  '--no-sandbox', '--disable-extensions',
  `--remote-debugging-port=${port}`, `--user-data-dir=${profile}`, 'about:blank',
], { stdio: 'ignore' })

async function eventually(action, timeout = 15000) {
  const end = Date.now() + timeout
  let last
  while (Date.now() < end) {
    try { return await action() } catch (error) { last = error; await new Promise(r => setTimeout(r, 150)) }
  }
  throw last
}

try {
  const target = await eventually(async () => {
    const response = await fetch(`http://127.0.0.1:${port}/json/list`)
    const pages = await response.json()
    const page = pages.find(item => item.type === 'page')
    if (!page) throw new Error('Chrome no abrió una página')
    return page.webSocketDebuggerUrl
  })
  const ws = new WebSocket(target)
  await new Promise((done, reject) => { ws.addEventListener('open', done, { once: true }); ws.addEventListener('error', reject, { once: true }) })
  let sequence = 0
  const pending = new Map()
  ws.addEventListener('close', () => {
    for (const { reject, timeout } of pending.values()) { clearTimeout(timeout); reject(new Error('Chrome cerró la conexión CDP')) }
    pending.clear()
  })
  ws.addEventListener('error', () => {
    for (const { reject, timeout } of pending.values()) { clearTimeout(timeout); reject(new Error('Fallo de conexión CDP')) }
    pending.clear()
  })
  ws.addEventListener('message', (message) => {
    const data = JSON.parse(message.data)
    if (!data.id || !pending.has(data.id)) return
    const { done, reject, timeout } = pending.get(data.id)
    clearTimeout(timeout)
    pending.delete(data.id)
    data.error ? reject(new Error(data.error.message)) : done(data.result)
  })
  const command = (method, params = {}) => new Promise((done, reject) => {
    const id = ++sequence
    const timeout = setTimeout(() => {
      if (pending.delete(id)) reject(new Error(`CDP no respondió a ${method}`))
    }, 5000)
    pending.set(id, { done, reject, timeout })
    ws.send(JSON.stringify({ id, method, params }))
  })
  const evaluate = async (expression) => {
    const result = await command('Runtime.evaluate', { expression, returnByValue: true, awaitPromise: true })
    if (result.exceptionDetails) throw new Error(result.exceptionDetails.text)
    return result.result.value
  }
  await command('Page.enable')
  await command('Page.navigate', { url: app })
  await eventually(async () => {
    if (!await evaluate("Boolean(document.querySelector('#demo-profile'))")) throw new Error('App no cargó')
  })
  const select = (role) => evaluate(`(() => { const field = document.querySelector('#demo-profile'); field.value = ${JSON.stringify(role)}; field.dispatchEvent(new Event('change', { bubbles: true })); return true })()`)
  const expectText = (text) => eventually(async () => {
    if (!await evaluate(`document.body.innerText.includes(${JSON.stringify(text)})`)) throw new Error(`Falta ${text}`)
  })
  await evaluate(`(() => {
    const original = window.fetch.bind(window)
    window.__ticketyaResponses = []
    window.fetch = async (...args) => {
      const response = await original(...args)
      const path = String(args[0])
      if (path.startsWith('/api/management/events') || path.startsWith('/api/venues')) {
        window.__ticketyaResponses.push({ path, status: response.status })
      }
      return response
    }
    return true
  })()`)
  const expectManagementApi = () => eventually(async () => {
    const responses = await evaluate('window.__ticketyaResponses')
    if (!responses.some(item => item.path === '/api/management/events') ||
        !responses.some(item => item.path.startsWith('/api/venues?'))) {
      throw new Error('Faltan respuestas de gestión o sedes')
    }
    const failed = responses.find(item => item.status !== 200)
    if (failed) throw new Error(`${failed.path} devolvió HTTP ${failed.status}`)
  })
  await expectText('Buscar eventos')
  await select('CLIENT')
  await expectText('Email de contacto')
  await select('ORGANIZER')
  await expectText('Gestión de eventos')
  await expectManagementApi()
  await evaluate('window.__ticketyaResponses = []')
  await select('ADMIN')
  await expectText('Sedes')
  await expectManagementApi()
  await evaluate("document.querySelector('.button-quiet').click()")
  await eventually(async () => {
    if (await evaluate("Boolean(document.querySelector('.button-quiet'))")) throw new Error('Salir no limpió sesión')
  })
  await expectText('Buscar eventos')
  ws.close()
  console.log('Browser smoke: CLIENT, ORGANIZER, ADMIN, APIs de gestión 200 y Salir OK')
} finally {
  chrome.kill()
  if (resolve(profile).startsWith(resolve(tmpdir()))) await rm(profile, { recursive: true, force: true, maxRetries: 5, retryDelay: 200 })
}
