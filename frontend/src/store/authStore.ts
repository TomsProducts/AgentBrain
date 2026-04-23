const TOKEN_KEY = 'ab_token'
const USER_KEY = 'ab_user'

export interface AuthUser {
  email: string
  name?: string
  role?: string
}

export const authStore = {
  getToken(): string | null {
    return localStorage.getItem(TOKEN_KEY)
  },

  getUser(): AuthUser | null {
    const raw = localStorage.getItem(USER_KEY)
    try { return raw ? JSON.parse(raw) : null } catch { return null }
  },

  setToken(token: string) {
    localStorage.setItem(TOKEN_KEY, token)
    // Decode JWT payload (no verify — backend handles that)
    try {
      const payload = JSON.parse(atob(token.split('.')[1]))
      const user: AuthUser = { email: payload.sub, name: payload.name, role: payload.role }
      localStorage.setItem(USER_KEY, JSON.stringify(user))
    } catch {}
  },

  clear() {
    localStorage.removeItem(TOKEN_KEY)
    localStorage.removeItem(USER_KEY)
  },

  isAuthenticated(): boolean {
    const token = this.getToken()
    if (!token) return false
    try {
      const payload = JSON.parse(atob(token.split('.')[1]))
      return payload.exp * 1000 > Date.now()
    } catch {
      return false
    }
  },

  redirectToLogin() {
    const identityUrl = 'http://192.168.68.111:3007'
    window.location.href = `${identityUrl}/login?redirect=${encodeURIComponent(window.location.origin)}`
  },
}
