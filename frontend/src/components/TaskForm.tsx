import { useState } from 'react'
import type { FormEvent } from 'react'
import type { TaskPayload, TaskStatus } from '../types/task'

interface TaskFormProps {
  onSubmit: (payload: TaskPayload) => Promise<void>
}

const STATUS_OPTIONS: { value: TaskStatus; label: string }[] = [
  { value: 'TODO', label: 'To Do' },
  { value: 'IN_PROGRESS', label: 'In Progress' },
  { value: 'DONE', label: 'Done' },
]

export default function TaskForm({ onSubmit }: TaskFormProps) {
  const [title, setTitle] = useState('')
  const [description, setDescription] = useState('')
  const [status, setStatus] = useState<TaskStatus>('TODO')
  const [errors, setErrors] = useState<{ title?: string; description?: string }>({})
  const [submitting, setSubmitting] = useState(false)

  const validate = (): boolean => {
    const next: typeof errors = {}
    if (!title.trim()) next.title = 'Title is required'
    else if (title.length > 100) next.title = 'Title must be at most 100 characters'
    if (description.length > 500) next.description = 'Description must be at most 500 characters'
    setErrors(next)
    return Object.keys(next).length === 0
  }

  const handleSubmit = async (event: FormEvent<HTMLFormElement>) => {
    event.preventDefault()
    if (!validate()) return
    setSubmitting(true)
    try {
      await onSubmit({ title: title.trim(), description: description.trim(), status })
      setTitle('')
      setDescription('')
      setStatus('TODO')
      setErrors({})
    } finally {
      setSubmitting(false)
    }
  }

  return (
    <form
      onSubmit={handleSubmit}
      className="rounded-2xl bg-white p-6 shadow-sm ring-1 ring-slate-200"
    >
      <h2 className="mb-4 text-lg font-semibold text-slate-800">Add New Task</h2>

      <div className="mb-4">
        <label htmlFor="title" className="mb-1 block text-sm font-medium text-slate-700">
          Title <span className="text-rose-500">*</span>
        </label>
        <input
          id="title"
          type="text"
          value={title}
          onChange={(e) => setTitle(e.target.value)}
          placeholder="e.g. Ship the release"
          className="w-full rounded-lg border border-slate-300 px-3 py-2 text-sm text-slate-900 placeholder-slate-400 focus:border-indigo-500 focus:outline-none focus:ring-2 focus:ring-indigo-200"
        />
        {errors.title && <p className="mt-1 text-sm text-rose-600">{errors.title}</p>}
      </div>

      <div className="mb-4">
        <label htmlFor="description" className="mb-1 block text-sm font-medium text-slate-700">
          Description
        </label>
        <textarea
          id="description"
          value={description}
          onChange={(e) => setDescription(e.target.value)}
          placeholder="Optional details about the task"
          rows={3}
          className="w-full resize-none rounded-lg border border-slate-300 px-3 py-2 text-sm text-slate-900 placeholder-slate-400 focus:border-indigo-500 focus:outline-none focus:ring-2 focus:ring-indigo-200"
        />
        {errors.description && (
          <p className="mt-1 text-sm text-rose-600">{errors.description}</p>
        )}
      </div>

      <div className="mb-6">
        <label htmlFor="status" className="mb-1 block text-sm font-medium text-slate-700">
          Status
        </label>
        <select
          id="status"
          value={status}
          onChange={(e) => setStatus(e.target.value as TaskStatus)}
          className="w-full rounded-lg border border-slate-300 bg-white px-3 py-2 text-sm text-slate-900 focus:border-indigo-500 focus:outline-none focus:ring-2 focus:ring-indigo-200"
        >
          {STATUS_OPTIONS.map((option) => (
            <option key={option.value} value={option.value}>
              {option.label}
            </option>
          ))}
        </select>
      </div>

      <button
        type="submit"
        disabled={submitting}
        className="w-full rounded-lg bg-indigo-600 px-4 py-2.5 text-sm font-semibold text-white transition hover:bg-indigo-700 disabled:cursor-not-allowed disabled:opacity-60"
      >
        {submitting ? 'Adding…' : 'Add Task'}
      </button>
    </form>
  )
}
