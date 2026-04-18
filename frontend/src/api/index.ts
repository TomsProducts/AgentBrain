const BASE = '/api'

export const memoryApi = {
  getWorking: () => fetch(`${BASE}/memory/working`).then(r => r.json()),
  addWorking: (data: { content: string; tags?: string }) =>
    fetch(`${BASE}/memory/working`, {
      method: 'POST',
      body: JSON.stringify(data),
      headers: { 'Content-Type': 'application/json' },
    }).then(r => r.json()),
  deleteWorking: (id: number) =>
    fetch(`${BASE}/memory/working/${id}`, { method: 'DELETE' }),

  getEpisodic: (page = 0, size = 20) =>
    fetch(`${BASE}/memory/episodic?page=${page}&size=${size}`).then(r => r.json()),
  addEpisodic: (data: { content: string; tags?: string }) =>
    fetch(`${BASE}/memory/episodic`, {
      method: 'POST',
      body: JSON.stringify(data),
      headers: { 'Content-Type': 'application/json' },
    }).then(r => r.json()),

  search: (q: string) =>
    fetch(`${BASE}/memory/search?q=${encodeURIComponent(q)}`).then(r => r.json()),
}

export const lessonApi = {
  list: (status?: string) =>
    fetch(`${BASE}/lessons${status ? `?status=${status}` : ''}`).then(r => r.json()),
  graduate: (id: number, rationale: string) =>
    fetch(`${BASE}/lessons/${id}/graduate`, {
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
    fetch(`${BASE}/lessons/${id}/reject`, {
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
    fetch(`${BASE}/lessons/${id}/reopen`, { method: 'POST' }).then(async r => {
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
  getTree: () => fetch(`${BASE}/claude/tree`).then(r => r.json()),
  readFile: (path: string) =>
    fetch(`${BASE}/claude/file?path=${encodeURIComponent(path)}`).then(r => r.text()),
  writeFile: (path: string, content: string) =>
    fetch(`${BASE}/claude/file?path=${encodeURIComponent(path)}`, {
      method: 'PUT',
      body: JSON.stringify({ content }),
      headers: { 'Content-Type': 'application/json' },
    }),
  deleteFile: (path: string) =>
    fetch(`${BASE}/claude/file?path=${encodeURIComponent(path)}`, { method: 'DELETE' }),
  createFile: (path: string, content = '') =>
    fetch(`${BASE}/claude/file?path=${encodeURIComponent(path)}`, {
      method: 'POST',
      body: JSON.stringify({ content }),
      headers: { 'Content-Type': 'application/json' },
    }),
}

export const dreamApi = {
  run: () => fetch(`${BASE}/dream/run`, { method: 'POST' }).then(r => r.json()),
  last: () => fetch(`${BASE}/dream/last`).then(r => r.json()),
}

export const statsApi = {
  get: () => fetch(`${BASE}/stats`).then(r => r.json()),
}

export const contextApi = {
  get: (q?: string) =>
    fetch(`${BASE}/context${q ? `?q=${encodeURIComponent(q)}` : ''}`).then(r => r.json()),
}
