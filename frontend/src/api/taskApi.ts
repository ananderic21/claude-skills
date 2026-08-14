import { apiFetch } from './client'
import type { Task, TaskPage, TaskPayload, TaskStatus } from '../types/task'

export function fetchTasks(): Promise<Task[]> {
  return apiFetch<Task[]>('/api/tasks')
}

export function fetchTaskPage(
  status: TaskStatus | 'ALL',
  page: number,
  size: number,
): Promise<TaskPage> {
  const params = new URLSearchParams({ page: String(page), size: String(size) })
  if (status !== 'ALL') params.set('status', status)
  return apiFetch<TaskPage>(`/api/tasks/page?${params.toString()}`)
}

export function createTask(payload: TaskPayload): Promise<Task> {
  return apiFetch<Task>('/api/tasks', {
    method: 'POST',
    body: JSON.stringify(payload),
  })
}

export function updateTask(id: number, payload: TaskPayload): Promise<Task> {
  return apiFetch<Task>(`/api/tasks/${id}`, {
    method: 'PUT',
    body: JSON.stringify(payload),
  })
}

export function deleteTask(id: number): Promise<void> {
  return apiFetch<void>(`/api/tasks/${id}`, { method: 'DELETE' })
}
