import { useState } from 'react'
import { useQuery, useMutation, useQueryClient } from '@tanstack/react-query'
import { memoryApi } from '../api'
import { Trash2, Plus, Search } from 'lucide-react'

type Tab = 'working' | 'episodic' | 'search'

export default function Memory() {
  const [tab, setTab] = useState<Tab>('working')
  const [page, setPage] = useState(0)
  const [searchQ, setSearchQ] = useState('')
  const [searchTerm, setSearchTerm] = useState('')
  const [addContent, setAddContent] = useState('')
  const [addTags, setAddTags] = useState('')
  const [showAdd, setShowAdd] = useState(false)

  const qc = useQueryClient()

  const workingQ = useQuery({
    queryKey: ['memory', 'working'],
    queryFn: memoryApi.getWorking,
    enabled: tab === 'working',
  })
  const episodicQ = useQuery({
    queryKey: ['memory', 'episodic', page],
    queryFn: () => memoryApi.getEpisodic(page),
    enabled: tab === 'episodic',
  })
  const searchQ2 = useQuery({
    queryKey: ['memory', 'search', searchTerm],
    queryFn: () => memoryApi.search(searchTerm),
    enabled: tab === 'search' && searchTerm.length > 0,
  })

  const addWorking = useMutation({
    mutationFn: () => memoryApi.addWorking({ content: addContent, tags: addTags }),
    onSuccess: () => { qc.invalidateQueries({ queryKey: ['memory', 'working'] }); setAddContent(''); setAddTags(''); setShowAdd(false) },
  })
  const addEpisodic = useMutation({
    mutationFn: () => memoryApi.addEpisodic({ content: addContent, tags: addTags }),
    onSuccess: () => { qc.invalidateQueries({ queryKey: ['memory', 'episodic'] }); setAddContent(''); setAddTags(''); setShowAdd(false) },
  })
  const deleteWorking = useMutation({
    mutationFn: (id: number) => memoryApi.deleteWorking(id),
    onSuccess: () => qc.invalidateQueries({ queryKey: ['memory', 'working'] }),
  })

  const tabs = [
    { id: 'working' as Tab, label: 'Working' },
    { id: 'episodic' as Tab, label: 'Episodic' },
    { id: 'search' as Tab, label: 'Search' },
  ]

  return (
    <div className="p-6 space-y-4">
      <div className="flex items-center justify-between">
        <h2 className="text-2xl font-bold text-white">Memory</h2>
        {tab !== 'search' && (
          <button onClick={() => setShowAdd(!showAdd)}
            className="flex items-center gap-1.5 bg-indigo-600 hover:bg-indigo-700 text-white px-3 py-1.5 rounded-lg text-sm">
            <Plus size={14} /> Add
          </button>
        )}
      </div>

      {/* Add form */}
      {showAdd && tab !== 'search' && (
        <div className="bg-slate-800 border border-slate-700 rounded-xl p-4 space-y-3">
          <textarea
            className="w-full bg-slate-900 border border-slate-600 rounded-lg p-3 text-sm text-slate-200 resize-none focus:outline-none focus:border-indigo-500"
            rows={3} placeholder="Content..." value={addContent} onChange={e => setAddContent(e.target.value)}
          />
          <input
            className="w-full bg-slate-900 border border-slate-600 rounded-lg px-3 py-2 text-sm text-slate-200 focus:outline-none focus:border-indigo-500"
            placeholder="Tags (optional)" value={addTags} onChange={e => setAddTags(e.target.value)}
          />
          <div className="flex gap-2">
            <button
              onClick={() => tab === 'working' ? addWorking.mutate() : addEpisodic.mutate()}
              disabled={!addContent.trim()}
              className="bg-indigo-600 hover:bg-indigo-700 disabled:opacity-40 text-white px-4 py-1.5 rounded-lg text-sm">
              Save
            </button>
            <button onClick={() => setShowAdd(false)} className="text-slate-400 hover:text-slate-200 px-4 py-1.5 text-sm">Cancel</button>
          </div>
        </div>
      )}

      {/* Tabs */}
      <div className="flex border-b border-slate-700">
        {tabs.map(t => (
          <button key={t.id} onClick={() => setTab(t.id)}
            className={`px-4 py-2 text-sm font-medium border-b-2 transition-colors ${
              tab === t.id ? 'border-indigo-500 text-indigo-400' : 'border-transparent text-slate-500 hover:text-slate-300'
            }`}>
            {t.label}
          </button>
        ))}
      </div>

      {/* Working Memory */}
      {tab === 'working' && (
        <div className="space-y-3">
          {workingQ.isLoading && <div className="text-slate-500 text-sm">Loading...</div>}
          {(workingQ.data ?? []).map((m: any) => (
            <div key={m.id} className="bg-slate-800/60 border border-slate-700 rounded-xl p-4">
              <div className="flex justify-between gap-2">
                <p className="text-sm text-slate-200 flex-1 whitespace-pre-wrap">{m.content}</p>
                <button onClick={() => deleteWorking.mutate(m.id)}
                  className="text-slate-600 hover:text-red-400 transition-colors shrink-0">
                  <Trash2 size={14} />
                </button>
              </div>
              <div className="flex gap-3 mt-2 text-xs text-slate-500">
                {m.tags && <span>#{m.tags}</span>}
                {m.expiresAt && <span>Expires {new Date(m.expiresAt).toLocaleDateString()}</span>}
              </div>
            </div>
          ))}
          {workingQ.data?.length === 0 && <div className="text-slate-600 text-sm">No working memories</div>}
        </div>
      )}

      {/* Episodic Memory */}
      {tab === 'episodic' && (
        <div className="space-y-3">
          {episodicQ.isLoading && <div className="text-slate-500 text-sm">Loading...</div>}
          {(episodicQ.data?.content ?? []).map((m: any) => (
            <div key={m.id} className="bg-slate-800/60 border border-slate-700 rounded-xl p-4">
              <p className="text-sm text-slate-200 whitespace-pre-wrap">{m.content}</p>
              <div className="flex gap-3 mt-2 items-center">
                <div className="flex items-center gap-1.5">
                  <div className="w-16 h-1.5 bg-slate-700 rounded-full overflow-hidden">
                    <div className="h-full bg-indigo-500 rounded-full"
                      style={{ width: `${(m.salienceScore ?? 1) * 100}%` }} />
                  </div>
                  <span className="text-xs text-slate-500">{((m.salienceScore ?? 1) * 100).toFixed(0)}%</span>
                </div>
                {m.tags && <span className="text-xs text-slate-500">#{m.tags}</span>}
                <span className="text-xs text-slate-600 ml-auto">{m.occurredAt ? new Date(m.occurredAt).toLocaleDateString() : ''}</span>
              </div>
            </div>
          ))}
          <div className="flex gap-2 justify-center pt-2">
            <button onClick={() => setPage(p => Math.max(0, p - 1))} disabled={page === 0}
              className="px-3 py-1 text-xs bg-slate-800 border border-slate-700 rounded disabled:opacity-40 hover:bg-slate-700">Prev</button>
            <span className="px-3 py-1 text-xs text-slate-400">Page {page + 1}</span>
            <button onClick={() => setPage(p => p + 1)} disabled={episodicQ.data?.last}
              className="px-3 py-1 text-xs bg-slate-800 border border-slate-700 rounded disabled:opacity-40 hover:bg-slate-700">Next</button>
          </div>
        </div>
      )}

      {/* Search */}
      {tab === 'search' && (
        <div className="space-y-3">
          <div className="flex gap-2">
            <div className="flex-1 relative">
              <Search size={14} className="absolute left-3 top-2.5 text-slate-500" />
              <input
                className="w-full bg-slate-900 border border-slate-700 rounded-lg pl-9 pr-3 py-2 text-sm text-slate-200 focus:outline-none focus:border-indigo-500"
                placeholder="Search all memory layers..." value={searchQ}
                onChange={e => setSearchQ(e.target.value)}
                onKeyDown={e => e.key === 'Enter' && setSearchTerm(searchQ)}
              />
            </div>
            <button onClick={() => setSearchTerm(searchQ)}
              className="bg-indigo-600 hover:bg-indigo-700 text-white px-4 py-2 rounded-lg text-sm">
              Search
            </button>
          </div>
          {searchQ2.data && (
            <div className="space-y-2">
              {searchQ2.data.workingMemory?.map((m: any) => (
                <div key={m.id} className="bg-slate-800/60 border border-purple-800/40 rounded-lg p-3">
                  <div className="text-xs text-purple-400 font-semibold mb-1">WORKING</div>
                  <p className="text-sm text-slate-200">{m.content}</p>
                </div>
              ))}
              {searchQ2.data.episodicMemory?.map((m: any) => (
                <div key={m.id} className="bg-slate-800/60 border border-blue-800/40 rounded-lg p-3">
                  <div className="text-xs text-blue-400 font-semibold mb-1">EPISODIC</div>
                  <p className="text-sm text-slate-200">{m.content}</p>
                </div>
              ))}
              {searchQ2.data.lessons?.map((l: any) => (
                <div key={l.id} className="bg-slate-800/60 border border-green-800/40 rounded-lg p-3">
                  <div className="text-xs text-green-400 font-semibold mb-1">LESSON · {l.status}</div>
                  <p className="text-sm text-slate-200">{l.claim}</p>
                </div>
              ))}
              {!searchQ2.data.workingMemory?.length && !searchQ2.data.episodicMemory?.length && !searchQ2.data.lessons?.length && (
                <div className="text-slate-500 text-sm">No results for "{searchTerm}"</div>
              )}
            </div>
          )}
        </div>
      )}
    </div>
  )
}
