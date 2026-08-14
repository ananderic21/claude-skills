import { createContext, useCallback, useContext, useEffect, useState } from 'react'
import type { ReactNode } from 'react'
import { login as apiLogin, register as apiRegister } from '../api/authApi'
import type { LoginPayload, RegisterPayload } from '../types/auth'
import { clearSession, loadSession, saveSession, UNAUTHORIZED_EVENT } from './session'
import type { Session } from './session'

interface AuthContextValue {
  session: Session | null
  login: (payload: LoginPayload) => Promise<void>
  register: (payload: RegisterPayload) => Promise<void>
  logout: () => void
}

const AuthContext = createContext<AuthContextValue | null>(null)

export function AuthProvider({ children }: { children: ReactNode }) {
  const [session, setSession] = useState<Session | null>(() => loadSession())

  const logout = useCallback(() => {
    clearSession()
    setSession(null)
  }, [])

  useEffect(() => {
    window.addEventListener(UNAUTHORIZED_EVENT, logout)
    return () => window.removeEventListener(UNAUTHORIZED_EVENT, logout)
  }, [logout])

  const login = useCallback(async (payload: LoginPayload) => {
    setSession(saveSession(await apiLogin(payload)))
  }, [])

  const register = useCallback(async (payload: RegisterPayload) => {
    setSession(saveSession(await apiRegister(payload)))
  }, [])

  return (
    <AuthContext.Provider value={{ session, login, register, logout }}>
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
