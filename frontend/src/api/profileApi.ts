import { apiFetch, apiFetchBlob } from './client'
import type {
  ChangePasswordPayload,
  Profile,
  ProfilePayload,
  ProfileUpdateResult,
} from '../types/profile'

export function getProfile(): Promise<Profile> {
  return apiFetch<Profile>('/api/profile')
}

export function updateProfile(payload: ProfilePayload): Promise<ProfileUpdateResult> {
  return apiFetch<ProfileUpdateResult>('/api/profile', {
    method: 'PUT',
    body: JSON.stringify(payload),
  })
}

export function changePassword(payload: ChangePasswordPayload): Promise<void> {
  return apiFetch<void>('/api/profile/password', {
    method: 'PUT',
    body: JSON.stringify(payload),
  })
}

export function uploadProfilePicture(file: File): Promise<Profile> {
  const form = new FormData()
  form.append('file', file)
  return apiFetch<Profile>('/api/profile/picture', { method: 'POST', body: form })
}

export function fetchProfilePicture(): Promise<Blob | null> {
  return apiFetchBlob('/api/profile/picture')
}

// Fired after a successful upload so every Avatar instance refetches
export const PICTURE_UPDATED_EVENT = 'profile:pictureUpdated'

export function notifyProfilePictureUpdated(): void {
  window.dispatchEvent(new Event(PICTURE_UPDATED_EVENT))
}
