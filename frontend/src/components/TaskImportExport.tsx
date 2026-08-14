import { useEffect, useRef, useState } from 'react'
import { exportTasks, exportTasksByStatus, pollImportStatus, startImport } from '../api/taskIoApi'
import type { ImportJobStatus } from '../types/taskIo'
import type { TaskStatus } from '../types/task'

const MAX_IMPORT_BYTES = 100 * 1024 * 1024

type StatusFilter = TaskStatus | 'ALL'

const FILTER_LABELS: Record<StatusFilter, string> = {
  ALL: 'tasks',
  TODO: 'To Do',
  IN_PROGRESS: 'In Progress',
  DONE: 'Done',
}

interface Props {
  selectedIds: number[]
  statusFilter: StatusFilter
  filteredCount: number
  onImportComplete: () => void
}

type Banner = { kind: 'info' | 'success' | 'error' | 'progress'; text: string }

const BANNER_STYLES: Record<Banner['kind'], string> = {
  info: 'border-sky-200 bg-sky-50 text-sky-700',
  success: 'border-emerald-200 bg-emerald-50 text-emerald-700',
  error: 'border-rose-200 bg-rose-50 text-rose-700',
  progress: 'border-amber-200 bg-amber-50 text-amber-700',
}

export default function TaskImportExport({
  selectedIds,
  statusFilter,
  filteredCount,
  onImportComplete,
}: Props) {
  const fileInputRef = useRef<HTMLInputElement>(null)
  const [exporting, setExporting] = useState(false)
  const [menuOpen, setMenuOpen] = useState(false)
  const [banner, setBanner] = useState<Banner | null>(null)
  const pollRef = useRef<ReturnType<typeof setInterval> | null>(null)

  // Clean up polling on unmount
  useEffect(() => {
    return () => {
      if (pollRef.current !== null) clearInterval(pollRef.current)
    }
  }, [])

  const runExport = async (fn: () => Promise<void>, successText: string) => {
    setMenuOpen(false)
    setExporting(true)
    setBanner(null)
    try {
      await fn()
      setBanner({ kind: 'success', text: successText })
    } catch (err) {
      setBanner({ kind: 'error', text: err instanceof Error ? err.message : 'Export failed' })
    } finally {
      setExporting(false)
    }
  }

  const handleExportSelected = () => {
    if (selectedIds.length === 0) {
      setMenuOpen(false)
      setBanner({ kind: 'error', text: 'Select at least one task to export' })
      return
    }
    const n = selectedIds.length
    void runExport(() => exportTasks(selectedIds), `Exported ${n} selected task${n === 1 ? '' : 's'}`)
  }

  const handleExportAll = () => {
    const label = statusFilter === 'ALL' ? 'all tasks' : `all ${FILTER_LABELS[statusFilter]} tasks`
    void runExport(() => exportTasksByStatus(statusFilter), `Exported ${label}`)
  }

  const handleFileSelected = async (file: File | undefined) => {
    if (!file) return
    if (!file.name.toLowerCase().endsWith('.json')) {
      setBanner({ kind: 'error', text: 'Only JSON files are supported' })
      return
    }
    if (file.size > MAX_IMPORT_BYTES) {
      setBanner({ kind: 'error', text: 'File is too large (max 100MB)' })
      return
    }
    if (fileInputRef.current) fileInputRef.current.value = ''

    setBanner({ kind: 'progress', text: 'Uploading…' })
    try {
      const job = await startImport(file)
      startPolling(job)
    } catch (err) {
      setBanner({ kind: 'error', text: err instanceof Error ? err.message : 'Import failed' })
    }
  }

  const startPolling = (initial: ImportJobStatus) => {
    if (pollRef.current !== null) clearInterval(pollRef.current)
    updateBannerFromJob(initial)
    if (isFinished(initial)) {
      handleFinishedJob(initial)
      return
    }
    pollRef.current = setInterval(async () => {
      try {
        const job = await pollImportStatus(initial.jobId)
        updateBannerFromJob(job)
        if (isFinished(job)) {
          clearInterval(pollRef.current!)
          pollRef.current = null
          handleFinishedJob(job)
        }
      } catch {
        clearInterval(pollRef.current!)
        pollRef.current = null
        setBanner({ kind: 'error', text: 'Lost contact with the import job' })
      }
    }, 1500)
  }

  const updateBannerFromJob = (job: ImportJobStatus) => {
    if (job.state === 'PENDING' || job.state === 'RUNNING') {
      const detail = job.imported > 0 ? ` (${job.imported} saved so far…)` : ''
      setBanner({ kind: 'progress', text: `Import in progress${detail}` })
    }
  }

  const handleFinishedJob = (job: ImportJobStatus) => {
    if (job.state === 'COMPLETED') {
      setBanner({ kind: 'success', text: `Imported ${job.imported} task${job.imported === 1 ? '' : 's'} successfully` })
      onImportComplete()
    } else if (job.state === 'COMPLETED_WITH_ERRORS') {
      setBanner({
        kind: 'success',
        text: `Imported ${job.imported} task${job.imported === 1 ? '' : 's'}; ${job.failed} row${job.failed === 1 ? '' : 's'} skipped (invalid)`,
      })
      onImportComplete()
    } else {
      setBanner({ kind: 'error', text: job.error ?? 'Import failed' })
    }
  }

  const isFinished = (job: ImportJobStatus) =>
    job.state === 'COMPLETED' || job.state === 'COMPLETED_WITH_ERRORS' || job.state === 'FAILED'

  const importBusy = banner?.kind === 'progress'

  return (
    <div className="space-y-3">
      {banner && (
        <div
          className={`rounded-xl border px-4 py-3 text-sm ${BANNER_STYLES[banner.kind]}`}
          role={banner.kind === 'error' ? 'alert' : 'status'}
        >
          {banner.kind === 'progress' && (
            <span className="mr-2 inline-block h-3 w-3 animate-spin rounded-full border-2 border-current border-t-transparent align-middle" />
          )}
          {banner.text}
        </div>
      )}

      <div className="flex flex-wrap items-center gap-2">
        <div className="relative">
          <button
            type="button"
            disabled={exporting}
            onClick={() => setMenuOpen((open) => !open)}
            aria-haspopup="menu"
            aria-expanded={menuOpen}
            className="flex items-center gap-1.5 rounded-xl border border-slate-300 px-3 py-2 text-sm font-medium text-slate-700 transition hover:bg-slate-50 disabled:cursor-not-allowed disabled:opacity-50"
          >
            <ExportIcon />
            {exporting ? 'Exporting…' : 'Export'}
            <CaretIcon />
          </button>

          {menuOpen && (
            <>
              {/* click-away backdrop */}
              <button
                type="button"
                aria-hidden="true"
                tabIndex={-1}
                className="fixed inset-0 z-10 cursor-default"
                onClick={() => setMenuOpen(false)}
              />
              <div
                role="menu"
                className="absolute right-0 z-20 mt-1 w-60 overflow-hidden rounded-xl border border-slate-200 bg-white py-1 shadow-lg"
              >
                <button
                  type="button"
                  role="menuitem"
                  disabled={selectedIds.length === 0}
                  onClick={handleExportSelected}
                  className="flex w-full items-center justify-between px-3 py-2 text-left text-sm text-slate-700 transition hover:bg-slate-50 disabled:cursor-not-allowed disabled:text-slate-400 disabled:hover:bg-transparent"
                >
                  <span>Selected tasks</span>
                  <span className="text-xs text-slate-400">{selectedIds.length}</span>
                </button>
                <button
                  type="button"
                  role="menuitem"
                  onClick={handleExportAll}
                  className="flex w-full items-center justify-between px-3 py-2 text-left text-sm text-slate-700 transition hover:bg-slate-50"
                >
                  <span>
                    All {statusFilter === 'ALL' ? 'tasks' : FILTER_LABELS[statusFilter]}
                  </span>
                  <span className="text-xs text-slate-400">{filteredCount}</span>
                </button>
              </div>
            </>
          )}
        </div>

        <input
          ref={fileInputRef}
          type="file"
          accept=".json,application/json"
          className="hidden"
          onChange={(e) => void handleFileSelected(e.target.files?.[0])}
        />
        <button
          type="button"
          disabled={importBusy}
          onClick={() => fileInputRef.current?.click()}
          className="flex items-center gap-1.5 rounded-xl border border-slate-300 px-3 py-2 text-sm font-medium text-slate-700 transition hover:bg-slate-50 disabled:cursor-not-allowed disabled:opacity-50"
        >
          <ImportIcon />
          {importBusy ? 'Importing…' : 'Import'}
        </button>
      </div>
    </div>
  )
}

function ExportIcon() {
  return (
    <svg className="h-4 w-4" viewBox="0 0 20 20" fill="currentColor" aria-hidden="true">
      <path
        fillRule="evenodd"
        d="M10 3a.75.75 0 0 1 .75.75v7.44l2.72-2.72a.75.75 0 1 1 1.06 1.06l-4 4a.75.75 0 0 1-1.06 0l-4-4a.75.75 0 1 1 1.06-1.06L9.25 11.19V3.75A.75.75 0 0 1 10 3ZM3.75 15a.75.75 0 0 0 0 1.5h12.5a.75.75 0 0 0 0-1.5H3.75Z"
        clipRule="evenodd"
      />
    </svg>
  )
}

function CaretIcon() {
  return (
    <svg className="h-3.5 w-3.5 text-slate-400" viewBox="0 0 20 20" fill="currentColor" aria-hidden="true">
      <path
        fillRule="evenodd"
        d="M5.22 8.22a.75.75 0 0 1 1.06 0L10 11.94l3.72-3.72a.75.75 0 1 1 1.06 1.06l-4.25 4.25a.75.75 0 0 1-1.06 0L5.22 9.28a.75.75 0 0 1 0-1.06Z"
        clipRule="evenodd"
      />
    </svg>
  )
}

function ImportIcon() {
  return (
    <svg className="h-4 w-4" viewBox="0 0 20 20" fill="currentColor" aria-hidden="true">
      <path
        fillRule="evenodd"
        d="M10 17a.75.75 0 0 1-.75-.75V8.81L6.53 11.53a.75.75 0 0 1-1.06-1.06l4-4a.75.75 0 0 1 1.06 0l4 4a.75.75 0 1 1-1.06 1.06L10.75 8.81v7.44A.75.75 0 0 1 10 17ZM3.75 3a.75.75 0 0 0 0 1.5h12.5a.75.75 0 0 0 0-1.5H3.75Z"
        clipRule="evenodd"
      />
    </svg>
  )
}
