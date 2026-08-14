import { useEffect, useRef, useState } from 'react'
import type { FormEvent } from 'react'
import {
  changePassword,
  getProfile,
  notifyProfilePictureUpdated,
  updateProfile,
  uploadProfilePicture,
} from '../api/profileApi'
import { useAuth } from '../auth/AuthContext'
import Avatar from './Avatar'
import type { Profile } from '../types/profile'

const MAX_PICTURE_BYTES = 2 * 1024 * 1024
const ALLOWED_PICTURE_TYPES = ['image/jpeg', 'image/png', 'image/webp']

const inputClass =
  'w-full rounded-xl border border-slate-300 px-3 py-2 text-sm text-slate-900 placeholder-slate-400 focus:border-sky-500 focus:outline-none focus:ring-2 focus:ring-sky-200'
const labelClass = 'mb-1 block text-sm font-medium text-slate-700'
const primaryButtonClass =
  'rounded-xl bg-slate-900 px-4 py-2.5 text-sm font-semibold text-white transition hover:bg-slate-700 disabled:cursor-not-allowed disabled:opacity-60'

interface Banner {
  kind: 'success' | 'error'
  text: string
}

function BannerMessage({ banner }: { banner: Banner }) {
  const styles =
    banner.kind === 'success'
      ? 'border-emerald-200 bg-emerald-50 text-emerald-700'
      : 'border-rose-200 bg-rose-50 text-rose-700'
  return (
    <div className={`mb-4 rounded-xl border px-4 py-3 text-sm ${styles}`}>{banner.text}</div>
  )
}

export default function ProfilePage({ onBack }: { onBack: () => void }) {
  const { session, applyAuth } = useAuth()

  const [profile, setProfile] = useState<Profile | null>(null)
  const [loadError, setLoadError] = useState<string | null>(null)

  // Profile details form
  const [name, setName] = useState('')
  const [username, setUsername] = useState('')
  const [email, setEmail] = useState('')
  const [detailsBanner, setDetailsBanner] = useState<Banner | null>(null)
  const [savingDetails, setSavingDetails] = useState(false)

  // Picture upload
  const fileInputRef = useRef<HTMLInputElement>(null)
  const [pictureBanner, setPictureBanner] = useState<Banner | null>(null)
  const [uploading, setUploading] = useState(false)

  // Password form
  const [currentPassword, setCurrentPassword] = useState('')
  const [newPassword, setNewPassword] = useState('')
  const [confirmPassword, setConfirmPassword] = useState('')
  const [passwordBanner, setPasswordBanner] = useState<Banner | null>(null)
  const [savingPassword, setSavingPassword] = useState(false)

  useEffect(() => {
    let cancelled = false
    getProfile()
      .then((data) => {
        if (cancelled) return
        setProfile(data)
        setName(data.name ?? '')
        setUsername(data.username)
        setEmail(data.email)
      })
      .catch((err) => {
        if (!cancelled) {
          setLoadError(err instanceof Error ? err.message : 'Failed to load profile')
        }
      })
    return () => {
      cancelled = true
    }
  }, [])

  const handleDetailsSubmit = async (event: FormEvent) => {
    event.preventDefault()
    const trimmedUsername = username.trim()
    if (trimmedUsername.length < 3) {
      setDetailsBanner({ kind: 'error', text: 'Username must be at least 3 characters' })
      return
    }
    if (!/^\S+@\S+\.\S+$/.test(email.trim())) {
      setDetailsBanner({ kind: 'error', text: 'Enter a valid email address' })
      return
    }
    setSavingDetails(true)
    setDetailsBanner(null)
    try {
      const result = await updateProfile({
        name: name.trim(),
        username: trimmedUsername,
        email: email.trim(),
      })
      setProfile(result.profile)
      setName(result.profile.name ?? '')
      setUsername(result.profile.username)
      setEmail(result.profile.email)
      if (result.auth) {
        // Username changed — switch to the freshly issued token
        applyAuth(result.auth)
      }
      setDetailsBanner({ kind: 'success', text: 'Profile updated' })
    } catch (err) {
      setDetailsBanner({
        kind: 'error',
        text: err instanceof Error ? err.message : 'Failed to update profile',
      })
    } finally {
      setSavingDetails(false)
    }
  }

  const handlePictureSelected = async (file: File | undefined) => {
    if (!file) return
    if (!ALLOWED_PICTURE_TYPES.includes(file.type)) {
      setPictureBanner({ kind: 'error', text: 'Only JPEG, PNG or WebP images are allowed' })
      return
    }
    if (file.size > MAX_PICTURE_BYTES) {
      setPictureBanner({ kind: 'error', text: 'Image must be smaller than 2MB' })
      return
    }
    setUploading(true)
    setPictureBanner(null)
    try {
      setProfile(await uploadProfilePicture(file))
      notifyProfilePictureUpdated()
      setPictureBanner({ kind: 'success', text: 'Profile picture updated' })
    } catch (err) {
      setPictureBanner({
        kind: 'error',
        text: err instanceof Error ? err.message : 'Failed to upload picture',
      })
    } finally {
      setUploading(false)
      if (fileInputRef.current) fileInputRef.current.value = ''
    }
  }

  const handlePasswordSubmit = async (event: FormEvent) => {
    event.preventDefault()
    if (newPassword.length < 8) {
      setPasswordBanner({ kind: 'error', text: 'New password must be at least 8 characters' })
      return
    }
    if (newPassword !== confirmPassword) {
      setPasswordBanner({ kind: 'error', text: 'Passwords do not match' })
      return
    }
    setSavingPassword(true)
    setPasswordBanner(null)
    try {
      await changePassword({ currentPassword, newPassword })
      setCurrentPassword('')
      setNewPassword('')
      setConfirmPassword('')
      setPasswordBanner({ kind: 'success', text: 'Password changed' })
    } catch (err) {
      setPasswordBanner({
        kind: 'error',
        text: err instanceof Error ? err.message : 'Failed to change password',
      })
    } finally {
      setSavingPassword(false)
    }
  }

  const displayName = profile?.name || session?.username || ''

  return (
    <div className="min-h-screen bg-slate-100">
      <header className="border-b border-slate-200 bg-white">
        <div className="mx-auto flex max-w-3xl items-center justify-between px-4 py-4 sm:px-6">
          <button
            type="button"
            onClick={onBack}
            className="rounded-lg border border-slate-300 px-3 py-1.5 text-sm font-medium text-slate-700 transition hover:bg-slate-50"
          >
            ← Back to Dashboard
          </button>
          <h1 className="text-xl font-bold text-slate-900">My Profile</h1>
        </div>
      </header>

      <main className="mx-auto max-w-3xl space-y-6 px-4 py-8 sm:px-6">
        {loadError && (
          <div className="rounded-xl border border-rose-200 bg-rose-50 px-4 py-3 text-sm text-rose-700">
            {loadError}
          </div>
        )}

        {!profile && !loadError && (
          <div className="rounded-2xl bg-white p-6 text-sm text-slate-500 shadow-sm ring-1 ring-slate-200">
            Loading profile…
          </div>
        )}

        {profile && (
          <>
            <section className="rounded-2xl bg-white p-6 shadow-sm ring-1 ring-slate-200">
              <h2 className="mb-4 text-base font-semibold text-slate-900">Profile Picture</h2>
              {pictureBanner && <BannerMessage banner={pictureBanner} />}
              <div className="flex items-center gap-5">
                <Avatar
                  username={profile.username}
                  sizeClass="h-24 w-24"
                  textClass="text-2xl"
                />
                <div>
                  <p className="text-sm font-semibold text-slate-900">{displayName}</p>
                  <p className="mb-3 text-sm text-slate-500">
                    JPEG, PNG or WebP — up to 2MB
                  </p>
                  <input
                    ref={fileInputRef}
                    type="file"
                    accept="image/jpeg,image/png,image/webp"
                    className="hidden"
                    onChange={(e) => void handlePictureSelected(e.target.files?.[0])}
                  />
                  <button
                    type="button"
                    disabled={uploading}
                    onClick={() => fileInputRef.current?.click()}
                    className="rounded-xl border border-slate-300 px-4 py-2 text-sm font-medium text-slate-700 transition hover:bg-slate-50 disabled:cursor-not-allowed disabled:opacity-60"
                  >
                    {uploading
                      ? 'Uploading…'
                      : profile.hasProfilePicture
                        ? 'Change photo'
                        : 'Upload photo'}
                  </button>
                </div>
              </div>
            </section>

            <section className="rounded-2xl bg-white p-6 shadow-sm ring-1 ring-slate-200">
              <h2 className="mb-4 text-base font-semibold text-slate-900">Profile Details</h2>
              {detailsBanner && <BannerMessage banner={detailsBanner} />}
              <form onSubmit={handleDetailsSubmit} className="space-y-4">
                <div>
                  <label htmlFor="profile-name" className={labelClass}>
                    Name
                  </label>
                  <input
                    id="profile-name"
                    type="text"
                    value={name}
                    onChange={(e) => setName(e.target.value)}
                    autoComplete="name"
                    className={inputClass}
                    placeholder="Your full name"
                  />
                </div>
                <div className="grid grid-cols-1 gap-4 sm:grid-cols-2">
                  <div>
                    <label htmlFor="profile-username" className={labelClass}>
                      Username
                    </label>
                    <input
                      id="profile-username"
                      type="text"
                      value={username}
                      onChange={(e) => setUsername(e.target.value)}
                      autoComplete="username"
                      className={inputClass}
                      placeholder="yourname"
                    />
                    <p className="mt-1 text-xs text-slate-500">
                      Must be unique — changing it signs you in with a new token automatically.
                    </p>
                  </div>
                  <div>
                    <label htmlFor="profile-email" className={labelClass}>
                      Email
                    </label>
                    <input
                      id="profile-email"
                      type="email"
                      value={email}
                      onChange={(e) => setEmail(e.target.value)}
                      autoComplete="email"
                      className={inputClass}
                      placeholder="you@example.com"
                    />
                  </div>
                </div>
                <button type="submit" disabled={savingDetails} className={primaryButtonClass}>
                  {savingDetails ? 'Saving…' : 'Save Changes'}
                </button>
              </form>
            </section>

            <section className="rounded-2xl bg-white p-6 shadow-sm ring-1 ring-slate-200">
              <h2 className="mb-4 text-base font-semibold text-slate-900">Change Password</h2>
              {passwordBanner && <BannerMessage banner={passwordBanner} />}
              <form onSubmit={handlePasswordSubmit} className="space-y-4">
                <div>
                  <label htmlFor="current-password" className={labelClass}>
                    Current password
                  </label>
                  <input
                    id="current-password"
                    type="password"
                    value={currentPassword}
                    onChange={(e) => setCurrentPassword(e.target.value)}
                    autoComplete="current-password"
                    className={inputClass}
                    placeholder="••••••••"
                  />
                </div>
                <div className="grid grid-cols-1 gap-4 sm:grid-cols-2">
                  <div>
                    <label htmlFor="new-password" className={labelClass}>
                      New password
                    </label>
                    <input
                      id="new-password"
                      type="password"
                      value={newPassword}
                      onChange={(e) => setNewPassword(e.target.value)}
                      autoComplete="new-password"
                      className={inputClass}
                      placeholder="At least 8 characters"
                    />
                  </div>
                  <div>
                    <label htmlFor="confirm-password" className={labelClass}>
                      Confirm new password
                    </label>
                    <input
                      id="confirm-password"
                      type="password"
                      value={confirmPassword}
                      onChange={(e) => setConfirmPassword(e.target.value)}
                      autoComplete="new-password"
                      className={inputClass}
                      placeholder="Repeat new password"
                    />
                  </div>
                </div>
                <button type="submit" disabled={savingPassword} className={primaryButtonClass}>
                  {savingPassword ? 'Changing…' : 'Change Password'}
                </button>
              </form>
            </section>
          </>
        )}
      </main>
    </div>
  )
}
