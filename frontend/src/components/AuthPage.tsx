import { useState } from 'react'
import type { FormEvent } from 'react'
import { forgotPassword } from '../api/authApi'
import { useAuth } from '../auth/AuthContext'

type Mode = 'login' | 'register' | 'forgot'

export default function AuthPage() {
  const { login, register } = useAuth()
  const [mode, setMode] = useState<Mode>('login')
  const [username, setUsername] = useState('')
  const [email, setEmail] = useState('')
  const [password, setPassword] = useState('')
  const [error, setError] = useState<string | null>(null)
  const [submitting, setSubmitting] = useState(false)
  const [notice, setNotice] = useState<string | null>(null)

  const isRegister = mode === 'register'
  const isForgot = mode === 'forgot'

  const validate = (): string | null => {
    if (isForgot) {
      if (!/^\S+@\S+\.\S+$/.test(email)) return 'Enter a valid email address'
      return null
    }
    if (!username.trim()) return 'Username is required'
    if (isRegister && username.trim().length < 3) {
      return 'Username must be at least 3 characters'
    }
    if (isRegister && !/^\S+@\S+\.\S+$/.test(email)) return 'Enter a valid email address'
    if (!password) return 'Password is required'
    if (isRegister && password.length < 8) {
      return 'Password must be at least 8 characters'
    }
    return null
  }

  const handleSubmit = async (event: FormEvent) => {
    event.preventDefault()
    const validationError = validate()
    if (validationError) {
      setError(validationError)
      return
    }
    setSubmitting(true)
    setError(null)
    try {
      if (isForgot) {
        const { message } = await forgotPassword(email.trim())
        setNotice(message)
      } else if (isRegister) {
        await register({ username: username.trim(), email: email.trim(), password })
      } else {
        await login({ username: username.trim(), password })
      }
    } catch (err) {
      setError(err instanceof Error ? err.message : 'Something went wrong')
    } finally {
      setSubmitting(false)
    }
  }

  const switchMode = (next: Mode) => {
    setMode(next)
    setError(null)
    setNotice(null)
  }

  return (
    <div className="flex min-h-screen items-center justify-center bg-slate-100 px-4">
      <div className="w-full max-w-md">
        <div className="mb-8 text-center">
          <h1 className="text-2xl font-bold text-slate-900">Task Dashboard</h1>
          <p className="mt-1 text-sm text-slate-500">
            {isForgot
              ? 'Enter your email and we’ll send you a reset link'
              : isRegister
                ? 'Create an account to manage your tasks'
                : 'Sign in to manage your tasks'}
          </p>
        </div>

        <div className="rounded-2xl bg-white p-6 shadow-sm ring-1 ring-slate-200">
          {!isForgot && (
            <div className="mb-6 grid grid-cols-2 gap-1 rounded-xl bg-slate-100 p-1">
              <button
                type="button"
                onClick={() => switchMode('login')}
                className={`rounded-lg px-3 py-2 text-sm font-medium transition ${
                  !isRegister ? 'bg-white text-slate-900 shadow-sm' : 'text-slate-500 hover:text-slate-700'
                }`}
              >
                Sign In
              </button>
              <button
                type="button"
                onClick={() => switchMode('register')}
                className={`rounded-lg px-3 py-2 text-sm font-medium transition ${
                  isRegister ? 'bg-white text-slate-900 shadow-sm' : 'text-slate-500 hover:text-slate-700'
                }`}
              >
                Create Account
              </button>
            </div>
          )}

          {error && (
            <div className="mb-4 rounded-xl border border-rose-200 bg-rose-50 px-4 py-3 text-sm text-rose-700">
              {error}
            </div>
          )}

          {notice && (
            <div className="mb-4 rounded-xl border border-emerald-200 bg-emerald-50 px-4 py-3 text-sm text-emerald-700">
              {notice}
            </div>
          )}

          <form onSubmit={handleSubmit} className="space-y-4">
            {!isForgot && (
              <div>
                <label htmlFor="username" className="mb-1 block text-sm font-medium text-slate-700">
                  Username
                </label>
                <input
                  id="username"
                  type="text"
                  value={username}
                  onChange={(e) => setUsername(e.target.value)}
                  autoComplete="username"
                  className="w-full rounded-xl border border-slate-300 px-3 py-2 text-sm text-slate-900 placeholder-slate-400 focus:border-sky-500 focus:outline-none focus:ring-2 focus:ring-sky-200"
                  placeholder="yourname"
                />
              </div>
            )}

            {(isRegister || isForgot) && (
              <div>
                <label htmlFor="email" className="mb-1 block text-sm font-medium text-slate-700">
                  Email
                </label>
                <input
                  id="email"
                  type="email"
                  value={email}
                  onChange={(e) => setEmail(e.target.value)}
                  autoComplete="email"
                  className="w-full rounded-xl border border-slate-300 px-3 py-2 text-sm text-slate-900 placeholder-slate-400 focus:border-sky-500 focus:outline-none focus:ring-2 focus:ring-sky-200"
                  placeholder="you@example.com"
                />
              </div>
            )}

            {!isForgot && (
              <div>
                <label htmlFor="password" className="mb-1 block text-sm font-medium text-slate-700">
                  Password
                </label>
                <input
                  id="password"
                  type="password"
                  value={password}
                  onChange={(e) => setPassword(e.target.value)}
                  autoComplete={isRegister ? 'new-password' : 'current-password'}
                  className="w-full rounded-xl border border-slate-300 px-3 py-2 text-sm text-slate-900 placeholder-slate-400 focus:border-sky-500 focus:outline-none focus:ring-2 focus:ring-sky-200"
                  placeholder={isRegister ? 'At least 8 characters' : '••••••••'}
                />
              </div>
            )}

            {mode === 'login' && (
              <div className="text-right">
                <button
                  type="button"
                  onClick={() => switchMode('forgot')}
                  className="text-sm font-medium text-sky-600 transition hover:text-sky-700"
                >
                  Forgot your password?
                </button>
              </div>
            )}

            <button
              type="submit"
              disabled={submitting}
              className="w-full rounded-xl bg-slate-900 px-4 py-2.5 text-sm font-semibold text-white transition hover:bg-slate-700 disabled:cursor-not-allowed disabled:opacity-60"
            >
              {submitting
                ? isForgot
                  ? 'Sending…'
                  : isRegister
                    ? 'Creating account…'
                    : 'Signing in…'
                : isForgot
                  ? 'Send reset link'
                  : isRegister
                    ? 'Create Account'
                    : 'Sign In'}
            </button>

            {isForgot && (
              <button
                type="button"
                onClick={() => switchMode('login')}
                className="w-full text-center text-sm font-medium text-slate-500 transition hover:text-slate-700"
              >
                Back to sign in
              </button>
            )}
          </form>
        </div>
      </div>
    </div>
  )
}
