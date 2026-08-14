import { API_BASE_URL, apiFetch } from './client'
import { getToken, notifyUnauthorized } from '../auth/session'
import type { ImportJobStatus } from '../types/taskIo'

export function exportTasks(ids: number[]): Promise<void> {
  const token = getToken()
  const headers: Record<string, string> = { 'Content-Type': 'application/json' }
  if (token) headers['Authorization'] = `Bearer ${token}`

  return fetch(`${API_BASE_URL}/api/tasks/export`, {
    method: 'POST',
    headers,
    body: JSON.stringify({ ids }),
  }).then(async (res) => {
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
  })
}

export function startImport(file: File): Promise<ImportJobStatus> {
  const form = new FormData()
  form.append('file', file)
  return apiFetch<ImportJobStatus>('/api/tasks/import', { method: 'POST', body: form })
}

export function pollImportStatus(jobId: string): Promise<ImportJobStatus> {
  return apiFetch<ImportJobStatus>(`/api/tasks/import/${jobId}`)
}
