import type { Task, TaskStatus } from '../types/task'

interface TaskListProps {
  tasks: Task[]
  loading: boolean
  selectedIds: Set<number>
  onToggleSelect: (id: number) => void
  onSelectAll: () => void
  onStatusChange: (task: Task, status: TaskStatus) => void
  onDelete: (id: number) => void
}

const STATUS_STYLES: Record<TaskStatus, string> = {
  TODO: 'bg-amber-100 text-amber-800',
  IN_PROGRESS: 'bg-sky-100 text-sky-800',
  DONE: 'bg-emerald-100 text-emerald-800',
}

const STATUS_LABELS: Record<TaskStatus, string> = {
  TODO: 'To Do',
  IN_PROGRESS: 'In Progress',
  DONE: 'Done',
}

export default function TaskList({
  tasks,
  loading,
  selectedIds,
  onToggleSelect,
  onSelectAll,
  onStatusChange,
  onDelete,
}: TaskListProps) {
  if (loading) {
    return (
      <div className="flex items-center justify-center rounded-2xl bg-white p-12 shadow-sm ring-1 ring-slate-200">
        <p className="text-sm text-slate-500">Loading tasks…</p>
      </div>
    )
  }

  if (tasks.length === 0) {
    return (
      <div className="flex flex-col items-center justify-center rounded-2xl bg-white p-12 shadow-sm ring-1 ring-slate-200">
        <p className="text-base font-medium text-slate-700">No tasks yet</p>
        <p className="mt-1 text-sm text-slate-500">Create your first task using the form.</p>
      </div>
    )
  }

  const allSelected = tasks.length > 0 && selectedIds.size === tasks.length
  const someSelected = selectedIds.size > 0 && !allSelected

  return (
    <div className="overflow-hidden rounded-2xl bg-white shadow-sm ring-1 ring-slate-200">
      <table className="min-w-full divide-y divide-slate-200">
        <thead className="bg-slate-50">
          <tr>
            <th className="w-10 px-4 py-3">
              <input
                type="checkbox"
                aria-label="Select all tasks"
                checked={allSelected}
                ref={(el) => {
                  if (el) el.indeterminate = someSelected
                }}
                onChange={onSelectAll}
                className="h-4 w-4 rounded border-slate-300 text-slate-900 focus:ring-sky-300"
              />
            </th>
            <th className="px-4 py-3 text-left text-xs font-semibold uppercase tracking-wider text-slate-500">
              Task
            </th>
            <th className="hidden px-4 py-3 text-left text-xs font-semibold uppercase tracking-wider text-slate-500 md:table-cell">
              Description
            </th>
            <th className="px-4 py-3 text-left text-xs font-semibold uppercase tracking-wider text-slate-500">
              Status
            </th>
            <th className="px-4 py-3 text-right text-xs font-semibold uppercase tracking-wider text-slate-500">
              Actions
            </th>
          </tr>
        </thead>
        <tbody className="divide-y divide-slate-100">
          {tasks.map((task) => (
            <tr
              key={task.id}
              className={`transition hover:bg-slate-50 ${selectedIds.has(task.id) ? 'bg-sky-50' : ''}`}
            >
              <td className="px-4 py-3">
                <input
                  type="checkbox"
                  aria-label={`Select "${task.title}"`}
                  checked={selectedIds.has(task.id)}
                  onChange={() => onToggleSelect(task.id)}
                  className="h-4 w-4 rounded border-slate-300 text-slate-900 focus:ring-sky-300"
                />
              </td>
              <td className="px-4 py-3">
                <span className="text-sm font-medium text-slate-900">{task.title}</span>
              </td>
              <td className="hidden max-w-xs px-4 py-3 md:table-cell">
                <span className="block truncate text-sm text-slate-500">
                  {task.description || '—'}
                </span>
              </td>
              <td className="px-4 py-3">
                <select
                  value={task.status}
                  onChange={(e) => onStatusChange(task, e.target.value as TaskStatus)}
                  className={`rounded-full px-2.5 py-1 text-xs font-semibold ${STATUS_STYLES[task.status]} cursor-pointer border-0 focus:outline-none focus:ring-2 focus:ring-indigo-300`}
                >
                  {(Object.keys(STATUS_LABELS) as TaskStatus[]).map((value) => (
                    <option key={value} value={value}>
                      {STATUS_LABELS[value]}
                    </option>
                  ))}
                </select>
              </td>
              <td className="px-4 py-3 text-right">
                <button
                  onClick={() => onDelete(task.id)}
                  className="rounded-lg px-3 py-1.5 text-sm font-medium text-rose-600 transition hover:bg-rose-50"
                >
                  Delete
                </button>
              </td>
            </tr>
          ))}
        </tbody>
      </table>
    </div>
  )
}
