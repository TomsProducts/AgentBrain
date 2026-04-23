import { authStore } from '../store/authStore'

const BASE = '/api'

function authHeaders(): HeadersInit {
  const token = authStore.getToken()
  return token ? { Authorization: `Bearer ${token}` } : {}
}

async function apiFetch(input: string, init: RequestInit = {}): Promise<Response> {
  const res = await fetch(input, {
    ...init,
    headers: { ...authHeaders(), ...(init.headers as Record<string, string> || {}) },
  })
  if (res.status === 401) {
    authStore.clear()
    authStore.redirectToLogin()
  }
  return res
}

export const memoryApi = {
  getWorking: () => apiFetch(`${BASE}/memory/working`).then(r => r.json()),
  addWorking: (data: { content: string; tags?: string }) =>
    apiFetch(`${BASE}/memory/working`, {
      method: 'POST',
      body: JSON.stringify(data),
      headers: { 'Content-Type': 'application/json' },
    }).then(r => r.json()),
  deleteWorking: (id: number) =>
    apiFetch(`${BASE}/memory/working/${id}`, { method: 'DELETE' }),

  getEpisodic: (page = 0, size = 20) =>
    apiFetch(`${BASE}/memory/episodic?page=${page}&size=${size}`).then(r => r.json()),
  addEpisodic: (data: { content: string; tags?: string }) =>
    apiFetch(`${BASE}/memory/episodic`, {
      method: 'POST',
      body: JSON.stringify(data),
      headers: { 'Content-Type': 'application/json' },
    }).then(r => r.json()),

  search: (q: string) =>
    apiFetch(`${BASE}/memory/search?q=${encodeURIComponent(q)}`).then(r => r.json()),
}

export const lessonApi = {
  list: (status?: string) =>
    apiFetch(`${BASE}/lessons${status ? `?status=${status}` : ''}`).then(r => r.json()),
  graduate: (id: number, rationale: string) =>
    apiFetch(`${BASE}/lessons/${id}/graduate`, {
      method: 'POST',
      body: JSON.stringify({ rationale }),
      headers: { 'Content-Type': 'application/json' },
    }).then(async r => {
      if (!r.ok) {
        const text = await r.text()
        let msg = `HTTP ${r.status}`
        try { const j = JSON.parse(text); msg = j.detail || j.message || j.error || msg } catch {}
        return Promise.reject(msg)
      }
      return r.json()
    }),
  reject: (id: number, reason?: string) =>
    apiFetch(`${BASE}/lessons/${id}/reject`, {
      method: 'POST',
      body: JSON.stringify({ reason }),
      headers: { 'Content-Type': 'application/json' },
    }).then(async r => {
      if (!r.ok) {
        const text = await r.text()
        let msg = `HTTP ${r.status}`
        try { const j = JSON.parse(text); msg = j.detail || j.message || j.error || msg } catch {}
        return Promise.reject(msg)
      }
      return r.json()
    }),
  reopen: (id: number) =>
    apiFetch(`${BASE}/lessons/${id}/reopen`, { method: 'POST' }).then(async r => {
      if (!r.ok) {
        const text = await r.text()
        let msg = `HTTP ${r.status}`
        try { const j = JSON.parse(text); msg = j.detail || j.message || j.error || msg } catch {}
        return Promise.reject(msg)
      }
      return r.json()
    }),
}

export const claudeApi = {
  getTree: () => apiFetch(`${BASE}/claude/tree`).then(r => r.json()),
  readFile: (path: string) =>
    apiFetch(`${BASE}/claude/file?path=${encodeURIComponent(path)}`).then(r => r.text()),
  writeFile: (path: string, content: string) =>
    apiFetch(`${BASE}/claude/file?path=${encodeURIComponent(path)}`, {
      method: 'PUT',
      body: JSON.stringify({ content }),
      headers: { 'Content-Type': 'application/json' },
    }),
  deleteFile: (path: string) =>
    apiFetch(`${BASE}/claude/file?path=${encodeURIComponent(path)}`, { method: 'DELETE' }),
  createFile: (path: string, content = '') =>
    apiFetch(`${BASE}/claude/file?path=${encodeURIComponent(path)}`, {
      method: 'POST',
      body: JSON.stringify({ content }),
      headers: { 'Content-Type': 'application/json' },
    }),
}

export const dreamApi = {
  run: () => apiFetch(`${BASE}/dream/run`, { method: 'POST' }).then(r => r.json()),
  last: () => apiFetch(`${BASE}/dream/last`).then(r => r.json()),
}

export const statsApi = {
  get: () => apiFetch(`${BASE}/stats`).then(r => r.json()),
}

export const contextApi = {
  get: (q?: string) =>
    apiFetch(`${BASE}/context${q ? `?q=${encodeURIComponent(q)}` : ''}`).then(r => r.json()),
}
