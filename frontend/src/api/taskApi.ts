import type { Task, TaskPayload } from '../types/task'

const BASE_URL = 'http://localhost:8080/api/tasks'

async function handleResponse<T>(response: Response): Promise<T> {
  if (!response.ok) {
    const body = await response.json().catch(() => null)
    const message =
      body && typeof body === 'object'
        ? Object.values(body).join(', ')
        : `Request failed with status ${response.status}`
    throw new Error(message)
  }
  if (response.status === 204) {
    return undefined as T
  }
  return response.json() as Promise<T>
}

export function fetchTasks(): Promise<Task[]> {
  return fetch(BASE_URL).then((res) => handleResponse<Task[]>(res))
}

export function createTask(payload: TaskPayload): Promise<Task> {
  return fetch(BASE_URL, {
    method: 'POST',
    headers: { 'Content-Type': 'application/json' },
    body: JSON.stringify(payload),
  }).then((res) => handleResponse<Task>(res))
}

export function updateTask(id: number, payload: TaskPayload): Promise<Task> {
  return fetch(`${BASE_URL}/${id}`, {
    method: 'PUT',
    headers: { 'Content-Type': 'application/json' },
    body: JSON.stringify(payload),
  }).then((res) => handleResponse<Task>(res))
}

export function deleteTask(id: number): Promise<void> {
  return fetch(`${BASE_URL}/${id}`, { method: 'DELETE' }).then((res) =>
    handleResponse<void>(res),
  )
}
