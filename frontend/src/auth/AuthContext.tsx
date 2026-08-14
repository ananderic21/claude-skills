import { createContext, useCallback, useContext, useEffect, useState } from 'react'
import type { ReactNode } from 'react'
import { login as apiLogin, logout as apiLogout, register as apiRegister } from '../api/authApi'
import type { AuthResponse, LoginPayload, RegisterPayload } from '../types/auth'
import { clearSession, loadSession, saveSession, UNAUTHORIZED_EVENT } from './session'
import type { Session } from './session'

interface AuthContextValue {
  session: Session | null
  login: (payload: LoginPayload) => Promise<void>
  register: (payload: RegisterPayload) => Promise<void>
  logout: () => void
  applyAuth: (auth: AuthResponse) => void
}

const AuthContext = createContext<AuthContextValue | null>(null)

export function AuthProvider({ children }: { children: ReactNode }) {
  const [session, setSession] = useState<Session | null>(() => loadSession())

  const clearLocalSession = useCallback(() => {
    clearSession()
    setSession(null)
  }, [])

  const logout = useCallback(() => {
    // Best-effort: records the logout time server-side; the local session is
    // cleared regardless of whether the call succeeds.
    apiLogout().catch(() => {})
    clearLocalSession()
  }, [clearLocalSession])

  useEffect(() => {
    // On 401 only clear locally — calling the logout API here would 401 again
    window.addEventListener(UNAUTHORIZED_EVENT, clearLocalSession)
    return () => window.removeEventListener(UNAUTHORIZED_EVENT, clearLocalSession)
  }, [clearLocalSession])

  const login = useCallback(async (payload: LoginPayload) => {
    setSession(saveSession(await apiLogin(payload)))
  }, [])

  const register = useCallback(async (payload: RegisterPayload) => {
    setSession(saveSession(await apiRegister(payload)))
  }, [])

  // Adopt a fresh token mid-session (e.g. after a username change)
  const applyAuth = useCallback((auth: AuthResponse) => {
    setSession(saveSession(auth))
  }, [])

  return (
    <AuthContext.Provider value={{ session, login, register, logout, applyAuth }}>
      {children}
    </AuthContext.Provider>
  )
}

// eslint-disable-next-line react-refresh/only-export-components
export function useAuth(): AuthContextValue {
  const context = useContext(AuthContext)
  if (!context) {
    throw new Error('useAuth must be used within an AuthProvider')
  }
  return context
}
