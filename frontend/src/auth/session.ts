import type { AuthResponse } from '../types/auth'

const TOKEN_KEY = 'auth.token'
const USERNAME_KEY = 'auth.username'
const EXPIRES_AT_KEY = 'auth.expiresAt'

export const UNAUTHORIZED_EVENT = 'auth:unauthorized'

export interface Session {
  token: string
  username: string
}

export function saveSession(auth: AuthResponse): Session {
  const expiresAt = Date.now() + auth.expiresInSeconds * 1000
  localStorage.setItem(TOKEN_KEY, auth.token)
  localStorage.setItem(USERNAME_KEY, auth.username)
  localStorage.setItem(EXPIRES_AT_KEY, String(expiresAt))
  return { token: auth.token, username: auth.username }
}

export function loadSession(): Session | null {
  const token = localStorage.getItem(TOKEN_KEY)
  const username = localStorage.getItem(USERNAME_KEY)
  const expiresAt = Number(localStorage.getItem(EXPIRES_AT_KEY))
  if (!token || !username || !expiresAt || Date.now() >= expiresAt) {
    clearSession()
    return null
  }
  return { token, username }
}

export function clearSession(): void {
  localStorage.removeItem(TOKEN_KEY)
  localStorage.removeItem(USERNAME_KEY)
  localStorage.removeItem(EXPIRES_AT_KEY)
}

export function getToken(): string | null {
  return loadSession()?.token ?? null
}

export function notifyUnauthorized(): void {
  window.dispatchEvent(new Event(UNAUTHORIZED_EVENT))
}
