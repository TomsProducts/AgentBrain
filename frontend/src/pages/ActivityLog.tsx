import { useState, useEffect, useRef } from 'react'
import { useActivityLog, ActivityEvent } from '../hooks/useActivityLog'

const EVENT_COLORS: Record<string, { badge: string; dot: string }> = {
  DREAM_COMPLETE:    { badge: 'bg-blue-900/60 text-blue-300 border-blue-700',   dot: 'bg-blue-500' },
  LESSON_GRADUATED:  { badge: 'bg-green-900/60 text-green-300 border-green-700', dot: 'bg-green-500' },
  LESSON_REJECTED:   { badge: 'bg-red-900/60 text-red-300 border-red-700',       dot: 'bg-red-500' },
  LESSON_REOPENED:   { badge: 'bg-yellow-900/60 text-yellow-300 border-yellow-700', dot: 'bg-yellow-400' },
  MEMORY_WRITE:      { badge: 'bg-slate-800 text-slate-400 border-slate-600',    dot: 'bg-slate-500' },
  ERROR:             { badge: 'bg-red-900/80 text-red-200 border-red-600',       dot: 'bg-red-600' },
}

const ALL_TYPES = Object.keys(EVENT_COLORS)

export default function ActivityLog() {
  const events = useActivityLog()
  const [filter, setFilter] = useState<string>('ALL')
  const [autoScroll, setAutoScroll] = useState(true)
  const bottomRef = useRef<HTMLDivElement>(null)

  const filtered = filter === 'ALL' ? events : events.filter(e => e.type === filter)

  useEffect(() => {
    if (autoScroll && bottomRef.current) {
      bottomRef.current.scrollIntoView({ behavior: 'smooth' })
    }
  }, [events, autoScroll])

  return (
    <div className="flex flex-col h-full p-6 gap-4">
      <div className="flex items-center justify-between">
        <div>
          <h2 className="text-2xl font-bold text-white">Activity Log</h2>
          <p className="text-slate-500 text-sm mt-0.5">{events.length} events (max 1000)</p>
        </div>
        <label className="flex items-center gap-2 text-sm text-slate-400 cursor-pointer">
          <input type="checkbox" checked={autoScroll} onChange={e => setAutoScroll(e.target.checked)}
            className="rounded" />
          Auto-scroll
        </label>
      </div>

      {/* Filter chips */}
      <div className="flex gap-2 flex-wrap">
        <button onClick={() => setFilter('ALL')}
          className={`px-3 py-1 rounded-full text-xs font-medium border transition-colors ${
            filter === 'ALL' ? 'bg-indigo-600 text-white border-indigo-500' : 'border-slate-700 text-slate-400 hover:border-slate-500'
          }`}>ALL</button>
        {ALL_TYPES.map(t => {
          const style = EVENT_COLORS[t] ?? { badge: 'border-slate-700 text-slate-400', dot: 'bg-slate-500' }
          return (
            <button key={t} onClick={() => setFilter(t)}
              className={`flex items-center gap-1.5 px-3 py-1 rounded-full text-xs font-medium border transition-colors ${
                filter === t ? style.badge : 'border-slate-700 text-slate-400 hover:border-slate-500'
              }`}>
              <span className={`w-1.5 h-1.5 rounded-full ${style.dot}`} />
              {t}
            </button>
          )
        })}
      </div>

      {/* Event list */}
      <div className="flex-1 overflow-auto bg-slate-900 border border-slate-700 rounded-xl divide-y divide-slate-800 font-mono text-xs">
        {filtered.length === 0 ? (
          <div className="p-6 text-center text-slate-600">
            <div className="text-3xl mb-2">📡</div>
            <div>Waiting for events...</div>
            <div className="text-slate-700 mt-1">Events appear here in real time via WebSocket</div>
          </div>
        ) : (
          [...filtered].reverse().map((ev, i) => {
            const style = EVENT_COLORS[ev.type] ?? { badge: 'border-slate-700 text-slate-400', dot: 'bg-slate-500' }
            return (
              <div key={i} className="flex items-start gap-3 px-4 py-2.5 hover:bg-slate-800/40 transition-colors">
                <span className="text-slate-600 w-24 shrink-0 text-right pt-0.5">
                  {new Date(ev.timestamp).toLocaleTimeString()}
                </span>
                <span className={`flex items-center gap-1.5 border px-2 py-0.5 rounded-full shrink-0 ${style.badge}`}>
                  <span className={`w-1.5 h-1.5 rounded-full ${style.dot}`} />
                  {ev.type}
                </span>
                <span className="text-slate-300 flex-1 break-all">{ev.message}</span>
              </div>
            )
          })
        )}
        <div ref={bottomRef} />
      </div>
    </div>
  )
}
