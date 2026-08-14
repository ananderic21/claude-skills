import { getToken, notifyUnauthorized } from '../auth/session'

export const API_BASE_URL = 'http://localhost:8080'

async function handleResponse<T>(response: Response): Promise<T> {
  if (response.status === 401) {
    notifyUnauthorized()
  }
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

export function apiFetch<T>(path: string, options: RequestInit = {}): Promise<T> {
  const headers = new Headers(options.headers)
  if (options.body !== undefined) {
    headers.set('Content-Type', 'application/json')
  }
  const token = getToken()
  if (token) {
    headers.set('Authorization', `Bearer ${token}`)
  }
  return fetch(`${API_BASE_URL}${path}`, { ...options, headers }).then((res) =>
    handleResponse<T>(res),
  )
}
