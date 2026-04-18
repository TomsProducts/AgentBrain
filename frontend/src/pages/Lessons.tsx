import { useState } from 'react'
import { useQuery, useMutation, useQueryClient } from '@tanstack/react-query'
import { lessonApi } from '../api'
import { CheckCircle, XCircle, RotateCcw, ChevronRight } from 'lucide-react'

type Tab = 'review' | 'accepted'

export default function Lessons() {
  const [tab, setTab] = useState<Tab>('review')
  const [selected, setSelected] = useState<any>(null)
  const [rationale, setRationale] = useState('')
  const [rejectReason, setRejectReason] = useState('')
  const [showRejectDialog, setShowRejectDialog] = useState(false)
  const qc = useQueryClient()

  const staged = useQuery({ queryKey: ['lessons', 'STAGED'], queryFn: () => lessonApi.list('STAGED') })
  const accepted = useQuery({ queryKey: ['lessons', 'ACCEPTED'], queryFn: () => lessonApi.list('ACCEPTED'), enabled: tab === 'accepted' })

  const graduate = useMutation({
    mutationFn: ({ id, rationale }: { id: number; rationale: string }) => lessonApi.graduate(id, rationale),
    onSuccess: () => { qc.invalidateQueries({ queryKey: ['lessons'] }); setSelected(null); setRationale('') },
    onError: (err: any) => alert('Graduate failed: ' + (typeof err === 'string' ? err : JSON.stringify(err))),
  })
  const reject = useMutation({
    mutationFn: ({ id, reason }: { id: number; reason: string }) => lessonApi.reject(id, reason),
    onSuccess: () => { qc.invalidateQueries({ queryKey: ['lessons'] }); setSelected(null); setShowRejectDialog(false); setRejectReason('') },
    onError: (err: any) => alert('Reject failed: ' + (typeof err === 'string' ? err : JSON.stringify(err))),
  })

  return (
    <div className="p-6 space-y-4">
      <h2 className="text-2xl font-bold text-white">Lessons</h2>

      <div className="flex border-b border-slate-700">
        {(['review', 'accepted'] as Tab[]).map(t => (
          <button key={t} onClick={() => setTab(t)}
            className={`px-4 py-2 text-sm font-medium border-b-2 transition-colors capitalize ${
              tab === t ? 'border-indigo-500 text-indigo-400' : 'border-transparent text-slate-500 hover:text-slate-300'
            }`}>
            {t === 'review' ? 'Review Queue' : 'Accepted'}
          </button>
        ))}
      </div>

      {/* Review Queue */}
      {tab === 'review' && (
        <div className="flex gap-4 h-[calc(100vh-220px)]">
          {/* List */}
          <div className="w-64 shrink-0 overflow-auto space-y-1">
            {staged.isLoading && <div className="text-slate-500 text-sm p-2">Loading...</div>}
            {(staged.data ?? []).filter((l: any) => l.status === 'STAGED' || l.status === 'REOPENED').map((l: any) => (
              <button key={l.id} onClick={() => { setSelected(l); setRationale('') }}
                className={`w-full text-left px-3 py-2.5 rounded-lg text-sm transition-colors flex items-center gap-2 ${
                  selected?.id === l.id ? 'bg-indigo-700 text-white' : 'bg-slate-800/50 hover:bg-slate-800 text-slate-300'
                }`}>
                <span className="flex-1 truncate">{l.claim}</span>
                <ChevronRight size={12} className="shrink-0 opacity-50" />
              </button>
            ))}
            {staged.data?.length === 0 && (
              <div className="text-slate-600 text-sm p-2">No lessons in review queue</div>
            )}
          </div>

          {/* Detail panel */}
          <div className="flex-1 bg-slate-800/40 border border-slate-700 rounded-xl p-5 overflow-auto">
            {selected ? (
              <div className="space-y-4">
                <div>
                  <div className="text-xs text-slate-500 uppercase tracking-wider mb-1">Claim</div>
                  <p className="text-slate-100 text-base font-medium">{selected.claim}</p>
                </div>
                {selected.conditions && (
                  <div>
                    <div className="text-xs text-slate-500 uppercase tracking-wider mb-1">Conditions</div>
                    <p className="text-slate-300 text-sm">{selected.conditions}</p>
                  </div>
                )}
                {selected.patternId && (
                  <div>
                    <div className="text-xs text-slate-500 uppercase tracking-wider mb-1">Pattern ID</div>
                    <code className="text-xs text-slate-400 bg-slate-900 px-2 py-1 rounded">{selected.patternId}</code>
                  </div>
                )}

                {/* Rationale */}
                <div>
                  <div className="flex justify-between items-center mb-1">
                    <div className="text-xs text-slate-500 uppercase tracking-wider">Rationale (required)</div>
                    <span className={`text-xs ${rationale.trim().length >= 10 ? 'text-green-400' : 'text-slate-500'}`}>
                      {rationale.trim().length}/10 min
                    </span>
                  </div>
                  <textarea
                    className="w-full bg-slate-900 border border-slate-600 rounded-lg p-3 text-sm text-slate-200 resize-none focus:outline-none focus:border-indigo-500"
                    rows={3} placeholder="Why should this lesson be accepted? (min 10 chars)"
                    value={rationale} onChange={e => setRationale(e.target.value)}
                    autoFocus
                  />
                </div>

                <div className="flex gap-2 items-center">
                  <button
                    onClick={() => graduate.mutate({ id: selected.id, rationale })}
                    disabled={rationale.trim().length < 10 || graduate.isPending}
                    className="flex items-center gap-1.5 bg-green-700 hover:bg-green-600 disabled:opacity-40 disabled:cursor-not-allowed text-white px-4 py-2 rounded-lg text-sm font-medium transition-colors">
                    <CheckCircle size={14} />
                    {graduate.isPending ? 'Saving...' : 'Graduate'}
                  </button>
                  <button
                    onClick={() => setShowRejectDialog(true)}
                    className="flex items-center gap-1.5 bg-red-900/60 hover:bg-red-800 text-red-300 px-4 py-2 rounded-lg text-sm font-medium transition-colors border border-red-800">
                    <XCircle size={14} /> Reject
                  </button>
                  {rationale.trim().length < 10 && (
                    <span className="text-xs text-slate-500">
                      {10 - rationale.trim().length} more chars needed
                    </span>
                  )}
                </div>

                {/* Reject dialog */}
                {showRejectDialog && (
                  <div className="border border-red-800 bg-red-950/40 rounded-xl p-4 space-y-3">
                    <div className="text-sm text-red-300 font-medium">Reject this lesson?</div>
                    <input
                      className="w-full bg-slate-900 border border-slate-700 rounded-lg px-3 py-2 text-sm text-slate-200 focus:outline-none"
                      placeholder="Reason (optional)" value={rejectReason} onChange={e => setRejectReason(e.target.value)}
                    />
                    <div className="flex gap-2">
                      <button onClick={() => reject.mutate({ id: selected.id, reason: rejectReason })}
                        className="bg-red-700 hover:bg-red-600 text-white px-3 py-1.5 rounded text-sm">Confirm Reject</button>
                      <button onClick={() => setShowRejectDialog(false)}
                        className="text-slate-400 hover:text-slate-200 px-3 py-1.5 text-sm">Cancel</button>
                    </div>
                  </div>
                )}
              </div>
            ) : (
              <div className="h-full flex items-center justify-center text-slate-600 text-sm">
                Select a lesson to review
              </div>
            )}
          </div>
        </div>
      )}

      {/* Accepted */}
      {tab === 'accepted' && (
        <div className="space-y-3">
          {accepted.isLoading && <div className="text-slate-500 text-sm">Loading...</div>}
          {(accepted.data ?? []).map((l: any) => (
            <div key={l.id} className="bg-slate-800/60 border border-slate-700 rounded-xl p-4 space-y-2">
              <div className="flex items-start gap-3">
                <div className="flex-1">
                  <p className="text-sm text-slate-100 font-medium">{l.claim}</p>
                  {l.rationale && <p className="text-xs text-slate-400 mt-1">{l.rationale}</p>}
                </div>
                <div className="shrink-0 text-right">
                  <div className="text-xs text-slate-500">{l.graduatedAt ? new Date(l.graduatedAt).toLocaleDateString() : ''}</div>
                </div>
              </div>
              <div className="flex items-center gap-2">
                <div className="flex items-center gap-1.5">
                  <div className="w-20 h-1 bg-slate-700 rounded-full overflow-hidden">
                    <div className="h-full bg-green-500 rounded-full" style={{ width: `${(l.salience ?? 1) * 100}%` }} />
                  </div>
                  <span className="text-xs text-slate-500">{((l.salience ?? 1) * 100).toFixed(0)}%</span>
                </div>
              </div>
            </div>
          ))}
          {accepted.data?.length === 0 && <div className="text-slate-600 text-sm">No accepted lessons yet</div>}
        </div>
      )}
    </div>
  )
}
