import { apiFetch } from './client'
import type { AuthResponse, LoginPayload, RegisterPayload } from '../types/auth'

export function login(payload: LoginPayload): Promise<AuthResponse> {
  return apiFetch<AuthResponse>('/api/auth/login', {
    method: 'POST',
    body: JSON.stringify(payload),
  })
}

export function register(payload: RegisterPayload): Promise<AuthResponse> {
  return apiFetch<AuthResponse>('/api/auth/register', {
    method: 'POST',
    body: JSON.stringify(payload),
  })
}

export function refreshToken(): Promise<AuthResponse> {
  return apiFetch<AuthResponse>('/api/auth/token/refresh', { method: 'POST' })
}
