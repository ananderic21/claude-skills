import { useCallback, useEffect, useState } from 'react'
import AuthPage from './components/AuthPage'
import Avatar from './components/Avatar'
import ProfilePage from './components/ProfilePage'
import TaskForm from './components/TaskForm'
import TaskImportExport from './components/TaskImportExport'
import TaskList from './components/TaskList'
import { createTask, deleteTask, fetchTasks, updateTask } from './api/taskApi'
import { useAuth } from './auth/AuthContext'
import type { Task, TaskPayload, TaskStatus } from './types/task'

export default function App() {
  const { session } = useAuth()
  const [view, setView] = useState<'dashboard' | 'profile'>('dashboard')

  useEffect(() => {
    if (!session) setView('dashboard')
  }, [session])

  if (!session) {
    return <AuthPage />
  }

  if (view === 'profile') {
    return <ProfilePage onBack={() => setView('dashboard')} />
  }

  return <Dashboard onOpenProfile={() => setView('profile')} />
}

function Dashboard({ onOpenProfile }: { onOpenProfile: () => void }) {
  const { session, logout } = useAuth()
  const [tasks, setTasks] = useState<Task[]>([])
  const [loading, setLoading] = useState(true)
  const [error, setError] = useState<string | null>(null)
  const [selectedIds, setSelectedIds] = useState<Set<number>>(new Set())

  const loadTasks = useCallback(async () => {
    setLoading(true)
    setError(null)
    try {
      setTasks(await fetchTasks())
      setSelectedIds(new Set())
    } catch (err) {
      setError(err instanceof Error ? err.message : 'Failed to load tasks')
    } finally {
      setLoading(false)
    }
  }, [])

  useEffect(() => {
    void loadTasks()
  }, [loadTasks])

  const handleCreate = async (payload: TaskPayload) => {
    try {
      const created = await createTask(payload)
      setTasks((prev) => [...prev, created])
      setError(null)
    } catch (err) {
      setError(err instanceof Error ? err.message : 'Failed to create task')
    }
  }

  const handleStatusChange = async (task: Task, status: TaskStatus) => {
    try {
      const updated = await updateTask(task.id, {
        title: task.title,
        description: task.description ?? '',
        status,
      })
      setTasks((prev) => prev.map((t) => (t.id === updated.id ? updated : t)))
      setError(null)
    } catch (err) {
      setError(err instanceof Error ? err.message : 'Failed to update task')
    }
  }

  const handleDelete = async (id: number) => {
    try {
      await deleteTask(id)
      setTasks((prev) => prev.filter((t) => t.id !== id))
      setSelectedIds((prev) => {
        const next = new Set(prev)
        next.delete(id)
        return next
      })
      setError(null)
    } catch (err) {
      setError(err instanceof Error ? err.message : 'Failed to delete task')
    }
  }

  const handleToggleSelect = (id: number) => {
    setSelectedIds((prev) => {
      const next = new Set(prev)
      if (next.has(id)) next.delete(id)
      else next.add(id)
      return next
    })
  }

  const handleSelectAll = () => {
    if (selectedIds.size === tasks.length) {
      setSelectedIds(new Set())
    } else {
      setSelectedIds(new Set(tasks.map((t) => t.id)))
    }
  }

  const doneCount = tasks.filter((t) => t.status === 'DONE').length
  const inProgressCount = tasks.filter((t) => t.status === 'IN_PROGRESS').length

  return (
    <div className="min-h-screen bg-slate-100">
      <header className="border-b border-slate-200 bg-white">
        <div className="mx-auto flex max-w-6xl items-center justify-between px-4 py-4 sm:px-6">
          <h1 className="text-xl font-bold text-slate-900">Task Dashboard</h1>
          <div className="flex items-center gap-4">
            <span className="text-sm text-slate-500">
              {tasks.length} task{tasks.length === 1 ? '' : 's'}
            </span>
            <button
              type="button"
              onClick={onOpenProfile}
              title="My Profile"
              className="flex items-center gap-2 rounded-full transition hover:opacity-80"
            >
              <Avatar username={session?.username ?? ''} />
              <span className="hidden text-sm font-medium text-slate-700 sm:inline">
                {session?.username}
              </span>
            </button>
            <button
              type="button"
              onClick={logout}
              className="rounded-lg border border-slate-300 px-3 py-1.5 text-sm font-medium text-slate-700 transition hover:bg-slate-50"
            >
              Sign out
            </button>
          </div>
        </div>
      </header>

      <main className="mx-auto max-w-6xl px-4 py-8 sm:px-6">
        <div className="mb-8 grid grid-cols-1 gap-4 sm:grid-cols-3">
          <div className="rounded-2xl bg-white p-5 shadow-sm ring-1 ring-slate-200">
            <p className="text-sm font-medium text-slate-500">Total Tasks</p>
            <p className="mt-1 text-3xl font-bold text-slate-900">{tasks.length}</p>
          </div>
          <div className="rounded-2xl bg-white p-5 shadow-sm ring-1 ring-slate-200">
            <p className="text-sm font-medium text-slate-500">In Progress</p>
            <p className="mt-1 text-3xl font-bold text-sky-600">{inProgressCount}</p>
          </div>
          <div className="rounded-2xl bg-white p-5 shadow-sm ring-1 ring-slate-200">
            <p className="text-sm font-medium text-slate-500">Completed</p>
            <p className="mt-1 text-3xl font-bold text-emerald-600">{doneCount}</p>
          </div>
        </div>

        {error && (
          <div className="mb-6 rounded-xl border border-rose-200 bg-rose-50 px-4 py-3 text-sm text-rose-700">
            {error}
          </div>
        )}

        <div className="grid grid-cols-1 gap-8 lg:grid-cols-3">
          <div className="lg:col-span-1 space-y-4">
            <TaskForm onSubmit={handleCreate} />
          </div>
          <div className="lg:col-span-2">
            <TaskList
              tasks={tasks}
              loading={loading}
              selectedIds={selectedIds}
              onToggleSelect={handleToggleSelect}
              onSelectAll={handleSelectAll}
              onStatusChange={handleStatusChange}
              onDelete={handleDelete}
              toolbar={
                <TaskImportExport
                  selectedIds={Array.from(selectedIds)}
                  onImportComplete={loadTasks}
                />
              }
            />
          </div>
        </div>
      </main>
    </div>
  )
}
