import { apiFetch } from './client'
import type { Task, TaskPayload } from '../types/task'

export function fetchTasks(): Promise<Task[]> {
  return apiFetch<Task[]>('/api/tasks')
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
