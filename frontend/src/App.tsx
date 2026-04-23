import { BrowserRouter, Routes, Route, NavLink, Navigate, useLocation, useNavigate } from 'react-router-dom'
import { useEffect, useState } from 'react'
import { Brain, BookOpen, Settings, Activity, LayoutDashboard, LogOut } from 'lucide-react'
import { authStore, AuthUser } from './store/authStore'
import Dashboard from './pages/Dashboard'
import Memory from './pages/Memory'
import Lessons from './pages/Lessons'
import ClaudeConfig from './pages/ClaudeConfig'
import ActivityLog from './pages/ActivityLog'

const navItems = [
  { to: '/', icon: LayoutDashboard, label: 'Dashboard' },
  { to: '/memory', icon: Brain, label: 'Memory' },
  { to: '/lessons', icon: BookOpen, label: 'Lessons' },
  { to: '/claude', icon: Settings, label: 'Claude Config' },
  { to: '/activity', icon: Activity, label: 'Activity' },
]

function SsoHandler({ onAuth }: { onAuth: () => void }) {
  const location = useLocation()
  const navigate = useNavigate()
  useEffect(() => {
    const params = new URLSearchParams(location.search)
    const token = params.get('sso_token')
    if (token) {
      authStore.setToken(token)
      params.delete('sso_token')
      navigate(location.pathname, { replace: true })
      onAuth()
    }
  }, [])
  return null
}

function ProtectedRoute({ children }: { children: React.ReactNode }) {
  if (!authStore.isAuthenticated()) {
    authStore.redirectToLogin()
    return null
  }
  return <>{children}</>
}

function App() {
  const [user, setUser] = useState<AuthUser | null>(authStore.getUser())

  const handleAuth = () => setUser(authStore.getUser())

  const handleLogout = () => {
    authStore.clear()
    authStore.redirectToLogin()
  }

  return (
    <BrowserRouter>
      <SsoHandler onAuth={handleAuth} />
      <div className="flex h-screen overflow-hidden">
        {/* Sidebar */}
        <nav className="w-56 bg-slate-900 border-r border-slate-700 flex flex-col">
          <div className="p-4 border-b border-slate-700">
            <h1 className="text-xl font-bold text-indigo-400">🧠 AgentBrain</h1>
            <p className="text-xs text-slate-500 mt-1">Agent Memory Dashboard</p>
          </div>
          <div className="flex-1 p-2">
            {navItems.map(({ to, icon: Icon, label }) => (
              <NavLink
                key={to}
                to={to}
                end={to === '/'}
                className={({ isActive }) =>
                  `flex items-center gap-3 px-3 py-2 rounded-lg mb-1 text-sm transition-colors ${
                    isActive
                      ? 'bg-indigo-600 text-white'
                      : 'text-slate-400 hover:bg-slate-800 hover:text-slate-200'
                  }`
                }
              >
                <Icon size={16} />
                {label}
              </NavLink>
            ))}
          </div>
          <div className="p-3 border-t border-slate-700">
            {user && (
              <div className="text-xs text-slate-400 mb-2 truncate" title={user.email}>
                {user.name || user.email}
              </div>
            )}
            <button
              onClick={handleLogout}
              className="flex items-center gap-2 text-xs text-slate-500 hover:text-red-400 transition-colors w-full"
            >
              <LogOut size={12} />
              Logout
            </button>
          </div>
        </nav>

        {/* Main content */}
        <main className="flex-1 overflow-auto bg-slate-950">
          <Routes>
            <Route path="/" element={<ProtectedRoute><Dashboard /></ProtectedRoute>} />
            <Route path="/memory" element={<ProtectedRoute><Memory /></ProtectedRoute>} />
            <Route path="/lessons" element={<ProtectedRoute><Lessons /></ProtectedRoute>} />
            <Route path="/claude" element={<ProtectedRoute><ClaudeConfig /></ProtectedRoute>} />
            <Route path="/activity" element={<ProtectedRoute><ActivityLog /></ProtectedRoute>} />
          </Routes>
        </main>
      </div>
    </BrowserRouter>
  )
}

export default App
