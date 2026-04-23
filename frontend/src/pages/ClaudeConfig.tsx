import { useState, useEffect } from 'react'
import { useQuery, useMutation, useQueryClient } from '@tanstack/react-query'
import { claudeApi } from '../api'
import { File, Folder, Save, Trash2, Plus, X } from 'lucide-react'

type FileNode = { name: string; path: string; type: 'file' | 'directory'; size: number; modified: number }

const READ_ONLY_PATHS = ['projects/', 'history.jsonl']

function isReadOnly(path: string) {
  return READ_ONLY_PATHS.some(p => path === p || path.startsWith(p))
}

function fileColor(node: FileNode) {
  if (node.name === 'CLAUDE.md') return 'text-green-400'
  if (node.name === 'settings.json') return 'text-blue-400'
  if (node.path.startsWith('skills/')) return 'text-purple-400'
  if (node.path.startsWith('agents/') || node.path.startsWith('commands/')) return 'text-orange-400'
  if (node.type === 'directory') return 'text-slate-300'
  return 'text-slate-400'
}

const FILE_TOOLTIPS: Record<string, string> = {
  'CLAUDE.md': 'Your global instructions — loaded every session',
  'settings.json': 'Permissions, hooks, model defaults',
  'skills': 'Auto-invoked skill workflows',
  'agents': 'Specialized subagent personas',
  'commands': 'Custom slash commands (/project:name)',
  'hooks': 'Pre/post tool call scripts',
  'rules': 'Modular instruction files',
  'projects': 'Session history (read-only)',
  'history.jsonl': 'Prompt history (read-only)',
}

export default function ClaudeConfig() {
  const qc = useQueryClient()
  const [selectedPath, setSelectedPath] = useState<string | null>(null)
  const [fileContent, setFileContent] = useState('')
  const [savedContent, setSavedContent] = useState('')
  const [showNewFile, setShowNewFile] = useState(false)
  const [newFilePath, setNewFilePath] = useState('')
  const [contextMenu, setContextMenu] = useState<{ x: number; y: number; node: FileNode } | null>(null)

  const tree = useQuery({ queryKey: ['claude', 'tree'], queryFn: claudeApi.getTree, refetchInterval: 5000 })

  const loadFile = async (path: string) => {
    const content = await claudeApi.readFile(path)
    setFileContent(content)
    setSavedContent(content)
    setSelectedPath(path)
  }

  const saveFile = useMutation({
    mutationFn: () => claudeApi.writeFile(selectedPath!, fileContent),
    onSuccess: () => { setSavedContent(fileContent); qc.invalidateQueries({ queryKey: ['claude', 'tree'] }) },
  })

  const deleteFile = useMutation({
    mutationFn: (path: string) => claudeApi.deleteFile(path),
    onSuccess: () => {
      qc.invalidateQueries({ queryKey: ['claude', 'tree'] })
      if (contextMenu?.node.path === selectedPath) { setSelectedPath(null); setFileContent('') }
      setContextMenu(null)
    },
  })

  const createFile = useMutation({
    mutationFn: () => claudeApi.createFile(newFilePath),
    onSuccess: () => { qc.invalidateQueries({ queryKey: ['claude', 'tree'] }); setNewFilePath(''); setShowNewFile(false); loadFile(newFilePath) },
  })

  useEffect(() => {
    const dismiss = () => setContextMenu(null)
    window.addEventListener('click', dismiss)
    return () => window.removeEventListener('click', dismiss)
  }, [])

  const nodes: FileNode[] = tree.data ?? []
  const rootFiles = nodes.filter(n => !n.path.includes('/'))
  const topDirs = [...new Set(nodes.filter(n => n.path.includes('/')).map(n => n.path.split('/')[0]))]

  const hasUnsaved = fileContent !== savedContent
  const readonly = selectedPath ? isReadOnly(selectedPath) : false

  return (
    <div className="flex h-full">
      {/* File tree panel */}
      <div className="w-64 shrink-0 border-r border-slate-700 bg-slate-900 overflow-auto">
        <div className="flex items-center justify-between px-3 py-2 border-b border-slate-800">
          <span className="text-xs font-semibold text-slate-400 uppercase tracking-wider">~/.claude/</span>
          <button onClick={() => setShowNewFile(true)}
            className="text-slate-500 hover:text-slate-200 p-1 rounded hover:bg-slate-800 transition-colors">
            <Plus size={12} />
          </button>
        </div>

        {showNewFile && (
          <div className="px-3 py-2 border-b border-slate-800 bg-slate-800/50 flex gap-1">
            <input autoFocus
              className="flex-1 bg-slate-900 border border-slate-600 rounded px-2 py-1 text-xs text-slate-200 focus:outline-none"
              placeholder="relative/path.md" value={newFilePath} onChange={e => setNewFilePath(e.target.value)}
              onKeyDown={e => { if (e.key === 'Enter' && newFilePath) createFile.mutate(); if (e.key === 'Escape') setShowNewFile(false) }}
            />
            <button onClick={() => setShowNewFile(false)} className="text-slate-500 hover:text-slate-300"><X size={12} /></button>
          </div>
        )}

        <div className="py-1">
          {/* Root files */}
          {rootFiles.map(n => (
            <div key={n.path} title={FILE_TOOLTIPS[n.name] ?? n.name}
              className={`flex items-center gap-2 px-3 py-1.5 text-xs cursor-pointer hover:bg-slate-800 transition-colors ${selectedPath === n.path ? 'bg-slate-700' : ''}`}
              onClick={() => n.type === 'file' && loadFile(n.path)}
              onContextMenu={e => { e.preventDefault(); setContextMenu({ x: e.clientX, y: e.clientY, node: n }) }}>
              <File size={12} className={fileColor(n)} />
              <span className={fileColor(n)}>{n.name}</span>
              {isReadOnly(n.path) && <span className="ml-auto text-slate-600 text-[10px]">ro</span>}
            </div>
          ))}
          {/* Directories */}
          {topDirs.map(dir => {
            const children = nodes.filter(n => n.path.startsWith(dir + '/'))
            const tooltip = FILE_TOOLTIPS[dir]
            return (
              <div key={dir}>
                <div title={tooltip} className="flex items-center gap-2 px-3 py-1.5 text-xs text-slate-300 cursor-default hover:bg-slate-800/50">
                  <Folder size={12} className={fileColor({ name: dir, path: dir, type: 'directory', size: 0, modified: 0 })} />
                  <span>{dir}/</span>
                </div>
                {children.map(n => (
                  <div key={n.path} title={n.path}
                    className={`flex items-center gap-2 pl-7 pr-3 py-1 text-xs cursor-pointer hover:bg-slate-800 transition-colors ${selectedPath === n.path ? 'bg-slate-700' : ''}`}
                    onClick={() => n.type === 'file' && loadFile(n.path)}
                    onContextMenu={e => { e.preventDefault(); setContextMenu({ x: e.clientX, y: e.clientY, node: n }) }}>
                    {n.type === 'file' ? <File size={11} className={fileColor(n)} /> : <Folder size={11} className="text-slate-500" />}
                    <span className={fileColor(n)}>{n.name}</span>
                    {isReadOnly(n.path) && <span className="ml-auto text-slate-600 text-[10px]">ro</span>}
                  </div>
                ))}
              </div>
            )
          })}
        </div>
      </div>

      {/* Context menu */}
      {contextMenu && (
        <div className="fixed z-50 bg-slate-800 border border-slate-600 rounded-lg shadow-xl py-1 text-sm"
          style={{ left: contextMenu.x, top: contextMenu.y }} onClick={e => e.stopPropagation()}>
          <button
            className="w-full text-left px-4 py-2 hover:bg-slate-700 text-red-400"
            onClick={() => deleteFile.mutate(contextMenu.node.path)}>
            <Trash2 size={12} className="inline mr-2" />Delete
          </button>
        </div>
      )}

      {/* Editor panel */}
      <div className="flex-1 flex flex-col overflow-hidden">
        {selectedPath ? (
          <>
            <div className="flex items-center gap-3 px-4 py-2.5 border-b border-slate-700 bg-slate-900/50">
              <span className="text-sm text-slate-300 font-mono">{selectedPath}</span>
              {readonly && <span className="text-xs bg-slate-700 text-slate-400 px-2 py-0.5 rounded">read-only</span>}
              {hasUnsaved && !readonly && <span className="text-xs text-yellow-400 ml-1">● unsaved changes</span>}
              <div className="ml-auto">
                <button
                  onClick={() => saveFile.mutate()}
                  disabled={readonly || !hasUnsaved || saveFile.isPending}
                  className="flex items-center gap-1.5 bg-indigo-600 hover:bg-indigo-700 disabled:opacity-40 text-white px-3 py-1.5 rounded-lg text-xs font-medium transition-colors">
                  <Save size={12} /> Save
                </button>
              </div>
            </div>
            <textarea
              className="flex-1 bg-slate-950 text-slate-200 font-mono text-sm p-4 resize-none focus:outline-none"
              value={fileContent}
              onChange={e => setFileContent(e.target.value)}
              readOnly={readonly}
              spellCheck={false}
            />
          </>
        ) : (
          <div className="flex-1 flex items-center justify-center text-slate-600">
            <div className="text-center">
              <div className="text-4xl mb-3">📁</div>
              <div className="text-sm">Select a file from the tree to edit</div>
            </div>
          </div>
        )}
      </div>
    </div>
  )
}
