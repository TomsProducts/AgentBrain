import { useState, useRef } from 'react'
import { useQuery, useMutation, useQueryClient } from '@tanstack/react-query'
import { Download, Upload, RotateCcw, HardDrive, CheckCircle, AlertCircle, Clock } from 'lucide-react'
import { backupApi } from '../api'

export default function Backup() {
  const qc = useQueryClient()
  const fileRef = useRef<HTMLInputElement>(null)
  const [importResult, setImportResult] = useState<{ working: number; episodic: number; lessons: number } | null>(null)
  const [importError, setImportError] = useState<string | null>(null)

  const { data: backups = [], isFetching } = useQuery({
    queryKey: ['backups'],
    queryFn: backupApi.list,
    refetchInterval: 30_000,
  })

  // Export: download ZIP
  const handleExport = async () => {
    try {
      const blob = await backupApi.export()
      const url = URL.createObjectURL(blob)
      const a = document.createElement('a')
      a.href = url
      a.download = `agentbrain-backup-${new Date().toISOString().slice(0, 16).replace('T', '_')}.zip`
      a.click()
      URL.revokeObjectURL(url)
    } catch {
      alert('Export failed')
    }
  }

  // Import: upload ZIP
  const handleFileChange = async (e: React.ChangeEvent<HTMLInputElement>) => {
    const file = e.target.files?.[0]
    if (!file) return
    setImportResult(null)
    setImportError(null)
    try {
      const result = await backupApi.import(file)
      setImportResult(result)
      qc.invalidateQueries({ queryKey: ['stats'] })
      qc.invalidateQueries({ queryKey: ['backups'] })
    } catch {
      setImportError('Import failed — check the file format')
    }
    e.target.value = ''
  }

  // Trigger manual backup
  const triggerMutation = useMutation({
    mutationFn: backupApi.triggerNow,
    onSuccess: () => qc.invalidateQueries({ queryKey: ['backups'] }),
  })

  // Restore from auto-backup
  const restoreMutation = useMutation({
    mutationFn: (name: string) => backupApi.restore(name),
    onSuccess: (result) => {
      setImportResult(result)
      qc.invalidateQueries({ queryKey: ['stats'] })
    },
  })

  return (
    <div className="p-6 space-y-6 max-w-3xl">
      <div>
        <h2 className="text-2xl font-bold text-white">Backup & Restore</h2>
        <p className="text-slate-400 text-sm mt-1">
          Backups are saved as Markdown files inside the <code className="text-indigo-300">/data/backups/</code> volume — they survive container rebuilds.
          Auto-backup runs every hour.
        </p>
      </div>

      {/* Actions */}
      <div className="grid grid-cols-1 sm:grid-cols-3 gap-4">
        {/* Export */}
        <button
          onClick={handleExport}
          className="flex flex-col items-center gap-2 bg-indigo-900/40 border border-indigo-700 rounded-xl p-5 hover:bg-indigo-900/60 transition-colors"
        >
          <Download size={24} className="text-indigo-400" />
          <span className="text-sm font-medium text-white">Export ZIP</span>
          <span className="text-xs text-slate-400">Download all data as Markdown</span>
        </button>

        {/* Import */}
        <button
          onClick={() => fileRef.current?.click()}
          className="flex flex-col items-center gap-2 bg-blue-900/40 border border-blue-700 rounded-xl p-5 hover:bg-blue-900/60 transition-colors"
        >
          <Upload size={24} className="text-blue-400" />
          <span className="text-sm font-medium text-white">Import ZIP</span>
          <span className="text-xs text-slate-400">Restore from a backup file</span>
        </button>
        <input ref={fileRef} type="file" accept=".zip" className="hidden" onChange={handleFileChange} />

        {/* Backup now */}
        <button
          onClick={() => triggerMutation.mutate()}
          disabled={triggerMutation.isPending}
          className="flex flex-col items-center gap-2 bg-green-900/40 border border-green-700 rounded-xl p-5 hover:bg-green-900/60 disabled:opacity-50 transition-colors"
        >
          <HardDrive size={24} className="text-green-400" />
          <span className="text-sm font-medium text-white">
            {triggerMutation.isPending ? 'Saving...' : 'Backup Now'}
          </span>
          <span className="text-xs text-slate-400">Save snapshot immediately</span>
        </button>
      </div>

      {/* Import/Restore result */}
      {importResult && (
        <div className="flex items-start gap-3 bg-green-900/20 border border-green-700 rounded-xl p-4">
          <CheckCircle size={18} className="text-green-400 mt-0.5 shrink-0" />
          <div className="text-sm text-green-300">
            Import complete — added <strong>{importResult.working}</strong> working memories,{' '}
            <strong>{importResult.episodic}</strong> episodic memories,{' '}
            <strong>{importResult.lessons}</strong> lessons.
            <span className="text-slate-400 ml-1">(Existing entries were skipped.)</span>
          </div>
        </div>
      )}
      {importError && (
        <div className="flex items-center gap-3 bg-red-900/20 border border-red-700 rounded-xl p-4">
          <AlertCircle size={18} className="text-red-400 shrink-0" />
          <span className="text-sm text-red-300">{importError}</span>
        </div>
      )}

      {/* Auto-backup list */}
      <div>
        <div className="flex items-center justify-between mb-3">
          <h3 className="text-sm font-semibold text-slate-300 flex items-center gap-2">
            <Clock size={14} />
            Auto-Backups ({backups.length})
          </h3>
          {isFetching && <span className="text-xs text-slate-500">Refreshing…</span>}
        </div>

        {backups.length === 0 ? (
          <div className="bg-slate-900 border border-slate-700 rounded-xl p-6 text-center text-slate-500 text-sm">
            No auto-backups yet — click <strong>Backup Now</strong> or wait for the hourly schedule.
          </div>
        ) : (
          <div className="bg-slate-900 border border-slate-700 rounded-xl divide-y divide-slate-800">
            {backups.map((name) => (
              <div key={name} className="flex items-center justify-between px-4 py-3">
                <span className="text-sm text-slate-300 font-mono">{name}</span>
                <button
                  onClick={() => restoreMutation.mutate(name)}
                  disabled={restoreMutation.isPending}
                  className="flex items-center gap-1.5 text-xs text-slate-400 hover:text-indigo-400 disabled:opacity-50 transition-colors"
                >
                  <RotateCcw size={12} />
                  Restore
                </button>
              </div>
            ))}
          </div>
        )}
      </div>
    </div>
  )
}
