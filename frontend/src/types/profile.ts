import type { AuthResponse } from './auth'

export interface Profile {
  username: string
  name: string | null
  email: string
  hasProfilePicture: boolean
  createdAt: string
}

export interface ProfilePayload {
  name: string
  username: string
  email: string
}

export interface ProfileUpdateResult {
  profile: Profile
  auth: AuthResponse | null
}

export interface ChangePasswordPayload {
  currentPassword: string
  newPassword: string
}
