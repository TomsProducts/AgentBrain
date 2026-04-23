import { BrowserRouter, Routes, Route, NavLink } from 'react-router-dom'
import { Brain, BookOpen, Settings, Activity, LayoutDashboard } from 'lucide-react'
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

function App() {
  return (
    <BrowserRouter>
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
          <div className="p-3 border-t border-slate-700 text-xs text-slate-600">
            AgentBrain v0.1
          </div>
        </nav>

        {/* Main content */}
        <main className="flex-1 overflow-auto bg-slate-950">
          <Routes>
            <Route path="/" element={<Dashboard />} />
            <Route path="/memory" element={<Memory />} />
            <Route path="/lessons" element={<Lessons />} />
            <Route path="/claude" element={<ClaudeConfig />} />
            <Route path="/activity" element={<ActivityLog />} />
          </Routes>
        </main>
      </div>
    </BrowserRouter>
  )
}

export default App
