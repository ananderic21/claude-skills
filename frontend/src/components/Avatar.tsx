import { useEffect, useState } from 'react'
import { fetchProfilePicture, PICTURE_UPDATED_EVENT } from '../api/profileApi'

interface AvatarProps {
  username: string
  sizeClass?: string
  textClass?: string
}

export default function Avatar({
  username,
  sizeClass = 'h-9 w-9',
  textClass = 'text-sm',
}: AvatarProps) {
  const [pictureUrl, setPictureUrl] = useState<string | null>(null)

  useEffect(() => {
    let objectUrl: string | null = null
    let cancelled = false

    const load = async () => {
      try {
        const blob = await fetchProfilePicture()
        if (cancelled) return
        if (objectUrl) URL.revokeObjectURL(objectUrl)
        objectUrl = blob ? URL.createObjectURL(blob) : null
        setPictureUrl(objectUrl)
      } catch {
        if (!cancelled) setPictureUrl(null)
      }
    }

    void load()
    window.addEventListener(PICTURE_UPDATED_EVENT, load)
    return () => {
      cancelled = true
      window.removeEventListener(PICTURE_UPDATED_EVENT, load)
      if (objectUrl) URL.revokeObjectURL(objectUrl)
    }
  }, [username])

  if (pictureUrl) {
    return (
      <img
        src={pictureUrl}
        alt={`${username}'s profile`}
        className={`${sizeClass} rounded-full object-cover ring-1 ring-slate-200`}
      />
    )
  }

  return (
    <div
      className={`${sizeClass} flex items-center justify-center rounded-full bg-slate-900 font-semibold text-white ${textClass}`}
      aria-label={`${username}'s profile`}
    >
      {username.slice(0, 2).toUpperCase()}
    </div>
  )
}
