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
  // FormData bodies must let the browser set the multipart boundary itself
  if (options.body !== undefined && !(options.body instanceof FormData)) {
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

// For binary responses (e.g. the profile picture); resolves to null on 404
export async function apiFetchBlob(path: string): Promise<Blob | null> {
  const headers = new Headers()
  const token = getToken()
  if (token) {
    headers.set('Authorization', `Bearer ${token}`)
  }
  const response = await fetch(`${API_BASE_URL}${path}`, { headers })
  if (response.status === 401) {
    notifyUnauthorized()
  }
  if (response.status === 404) {
    return null
  }
  if (!response.ok) {
    throw new Error(`Request failed with status ${response.status}`)
  }
  return response.blob()
}
