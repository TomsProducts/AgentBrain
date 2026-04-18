import { useQuery, useMutation, useQueryClient } from '@tanstack/react-query'
import { statsApi, dreamApi } from '../api'
import { useActivityLog } from '../hooks/useActivityLog'
import { Link } from 'react-router-dom'
import { Brain, BookOpen, Settings, Zap, Activity } from 'lucide-react'

const eventColors: Record<string, string> = {
  DREAM_COMPLETE: 'text-blue-400',
  LESSON_GRADUATED: 'text-green-400',
  LESSON_REJECTED: 'text-red-400',
  LESSON_REOPENED: 'text-yellow-400',
  MEMORY_WRITE: 'text-slate-400',
  ERROR: 'text-red-500',
}

export default function Dashboard() {
  const qc = useQueryClient()
  const { data: stats } = useQuery({ queryKey: ['stats'], queryFn: statsApi.get, refetchInterval: 10_000 })
  const events = useActivityLog()

  const dreamMutation = useMutation({
    mutationFn: dreamApi.run,
    onSuccess: () => qc.invalidateQueries({ queryKey: ['stats'] }),
  })

  const cards = [
    { label: 'Working Memory', value: stats?.workingMemoryCount ?? '—', color: 'bg-purple-900/30 border-purple-700', link: '/memory' },
    { label: 'Episodic Memory', value: stats?.episodicMemoryCount ?? '—', color: 'bg-blue-900/30 border-blue-700', link: '/memory' },
    { label: 'Staged Lessons', value: stats?.stagedLessons ?? '—', color: 'bg-yellow-900/30 border-yellow-700', link: '/lessons' },
    { label: 'Accepted Lessons', value: stats?.acceptedLessons ?? '—', color: 'bg-green-900/30 border-green-700', link: '/lessons' },
  ]

  return (
    <div className="p-6 space-y-6">
      <div className="flex items-center justify-between">
        <div>
          <h2 className="text-2xl font-bold text-white">Dashboard</h2>
          <p className="text-slate-400 text-sm mt-1">
            Last dream: <span className="text-slate-300">{stats?.lastDreamRun ?? 'never'}</span>
            {stats?.lastDreamResult && <span className="ml-2 text-slate-500">— {stats.lastDreamResult}</span>}
          </p>
        </div>
        <button
          onClick={() => dreamMutation.mutate()}
          disabled={dreamMutation.isPending}
          className="flex items-center gap-2 bg-indigo-600 hover:bg-indigo-700 disabled:opacity-50 text-white px-4 py-2 rounded-lg text-sm font-medium transition-colors"
        >
          <Zap size={14} />
          {dreamMutation.isPending ? 'Running...' : 'Run Dream Now'}
        </button>
      </div>

      {/* Stats row */}
      <div className="grid grid-cols-2 lg:grid-cols-4 gap-4">
        {cards.map(card => (
          <Link key={card.label} to={card.link}
            className={`border rounded-xl p-4 ${card.color} hover:opacity-80 transition-opacity`}>
            <div className="text-3xl font-bold text-white">{card.value}</div>
            <div className="text-sm text-slate-400 mt-1">{card.label}</div>
          </Link>
        ))}
      </div>

      {/* Quick links */}
      <div className="grid grid-cols-2 lg:grid-cols-4 gap-3">
        {[
          { to: '/memory', icon: Brain, label: 'Manage Memory', desc: 'View and add memories' },
          { to: '/lessons', icon: BookOpen, label: 'Review Lessons', desc: 'Graduate staged lessons' },
          { to: '/claude', icon: Settings, label: 'Claude Config', desc: 'Edit ~/.claude/ files' },
          { to: '/activity', icon: Activity, label: 'Activity Log', desc: 'Live event stream' },
        ].map(({ to, icon: Icon, label, desc }) => (
          <Link key={to} to={to}
            className="bg-slate-800/50 border border-slate-700 rounded-xl p-4 hover:bg-slate-800 transition-colors">
            <Icon size={20} className="text-indigo-400 mb-2" />
            <div className="text-sm font-medium text-white">{label}</div>
            <div className="text-xs text-slate-500 mt-0.5">{desc}</div>
          </Link>
        ))}
      </div>

      {/* Live activity strip */}
      <div>
        <h3 className="text-sm font-medium text-slate-400 mb-2">Recent Activity</h3>
        <div className="bg-slate-900 border border-slate-700 rounded-xl divide-y divide-slate-800">
          {events.slice(0, 5).length === 0 ? (
            <div className="p-4 text-slate-600 text-sm">No events yet — activity will appear here live</div>
          ) : (
            events.slice(0, 5).map((ev, i) => (
              <div key={i} className="flex items-center gap-3 px-4 py-2.5 text-sm">
                <span className={`font-mono text-xs font-semibold ${eventColors[ev.type] ?? 'text-slate-400'}`}>
                  {ev.type}
                </span>
                <span className="text-slate-300 flex-1">{ev.message}</span>
                <span className="text-slate-600 text-xs">{new Date(ev.timestamp).toLocaleTimeString()}</span>
              </div>
            ))
          )}
        </div>
      </div>
    </div>
  )
}
