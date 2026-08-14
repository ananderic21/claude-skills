import { API_BASE_URL, apiFetch } from './client'
import { getToken, notifyUnauthorized } from '../auth/session'
import type { ImportJobStatus } from '../types/taskIo'
import type { TaskStatus } from '../types/task'

function authHeaders(extra: Record<string, string> = {}): Record<string, string> {
  const token = getToken()
  const headers = { ...extra }
  if (token) headers['Authorization'] = `Bearer ${token}`
  return headers
}

// Turns an export response into a browser file download.
async function triggerDownload(res: Response): Promise<void> {
  if (res.status === 401) {
    notifyUnauthorized()
    throw new Error('Unauthorized')
  }
  if (!res.ok) {
    const body = await res.json().catch(() => null)
    throw new Error(
      body && typeof body === 'object' ? Object.values(body).join(', ') : `Export failed (${res.status})`,
    )
  }
  // Derive filename from Content-Disposition if present, fall back to default
  const disposition = res.headers.get('Content-Disposition') ?? ''
  const match = /filename="?([^"]+)"?/.exec(disposition)
  const filename = match ? match[1] : 'tasks-export.json'
  const blob = await res.blob()
  const url = URL.createObjectURL(blob)
  const a = document.createElement('a')
  a.href = url
  a.download = filename
  document.body.appendChild(a)
  a.click()
  a.remove()
  URL.revokeObjectURL(url)
}

// Export only the tasks the user hand-picked (by id).
export function exportTasks(ids: number[]): Promise<void> {
  return fetch(`${API_BASE_URL}/api/tasks/export`, {
    method: 'POST',
    headers: authHeaders({ 'Content-Type': 'application/json' }),
    body: JSON.stringify({ ids }),
  }).then(triggerDownload)
}

// Export every task matching a status ('ALL' exports the whole table).
export function exportTasksByStatus(status: TaskStatus | 'ALL'): Promise<void> {
  const query = status === 'ALL' ? '' : `?status=${status}`
  return fetch(`${API_BASE_URL}/api/tasks/export/all${query}`, {
    method: 'GET',
    headers: authHeaders(),
  }).then(triggerDownload)
}

export function startImport(file: File): Promise<ImportJobStatus> {
  const form = new FormData()
  form.append('file', file)
  return apiFetch<ImportJobStatus>('/api/tasks/import', { method: 'POST', body: form })
}

export function pollImportStatus(jobId: string): Promise<ImportJobStatus> {
  return apiFetch<ImportJobStatus>(`/api/tasks/import/${jobId}`)
}
