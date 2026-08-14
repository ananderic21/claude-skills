import type { ReactNode } from 'react'
import type { Task, TaskStatus } from '../types/task'

type StatusFilter = TaskStatus | 'ALL'

interface TaskListProps {
  tasks: Task[]
  loading: boolean
  selectedIds: Set<number>
  onToggleSelect: (id: number) => void
  onSelectAll: () => void
  onStatusChange: (task: Task, status: TaskStatus) => void
  onDelete: (id: number) => void
  toolbar?: ReactNode
  statusFilter: StatusFilter
  onStatusFilterChange: (value: StatusFilter) => void
  page: number
  totalPages: number
  totalElements: number
  pageSize: number
  onPageChange: (page: number) => void
}

const FILTER_OPTIONS: { value: StatusFilter; label: string }[] = [
  { value: 'ALL', label: 'All statuses' },
  { value: 'TODO', label: 'To Do' },
  { value: 'IN_PROGRESS', label: 'In Progress' },
  { value: 'DONE', label: 'Done' },
]

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
  toolbar,
  statusFilter,
  onStatusFilterChange,
  page,
  totalPages,
  totalElements,
  pageSize,
  onPageChange,
}: TaskListProps) {
  const allSelected = tasks.length > 0 && selectedIds.size === tasks.length
  const someSelected = selectedIds.size > 0 && !allSelected

  const rangeStart = totalElements === 0 ? 0 : page * pageSize + 1
  const rangeEnd = Math.min(page * pageSize + tasks.length, totalElements)

  return (
    <div className="overflow-hidden rounded-2xl bg-white shadow-sm ring-1 ring-slate-200">
      <div className="flex flex-col gap-3 border-b border-slate-200 px-4 py-3 sm:flex-row sm:items-center sm:justify-between">
        <div className="flex items-center gap-3">
          <h2 className="text-sm font-semibold text-slate-700">Tasks</h2>
          <select
            aria-label="Filter tasks by status"
            value={statusFilter}
            onChange={(e) => onStatusFilterChange(e.target.value as StatusFilter)}
            className="rounded-lg border border-slate-300 bg-white px-2.5 py-1.5 text-sm text-slate-700 focus:outline-none focus:ring-2 focus:ring-sky-300"
          >
            {FILTER_OPTIONS.map((opt) => (
              <option key={opt.value} value={opt.value}>
                {opt.label}
              </option>
            ))}
          </select>
        </div>
        {toolbar}
      </div>

      {loading ? (
        <div className="flex items-center justify-center p-12">
          <p className="text-sm text-slate-500">Loading tasks…</p>
        </div>
      ) : tasks.length === 0 ? (
        <div className="flex flex-col items-center justify-center p-12">
          <p className="text-base font-medium text-slate-700">No tasks yet</p>
          <p className="mt-1 text-sm text-slate-500">Create your first task using the form.</p>
        </div>
      ) : (
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
      )}

      {!loading && totalElements > 0 && (
        <div className="flex flex-col gap-3 border-t border-slate-200 px-4 py-3 text-sm text-slate-600 sm:flex-row sm:items-center sm:justify-between">
          <span>
            Showing <span className="font-medium text-slate-900">{rangeStart}</span>–
            <span className="font-medium text-slate-900">{rangeEnd}</span> of{' '}
            <span className="font-medium text-slate-900">{totalElements}</span>
          </span>
          <div className="flex items-center gap-2">
            <button
              type="button"
              onClick={() => onPageChange(page - 1)}
              disabled={page <= 0}
              className="rounded-lg border border-slate-300 px-3 py-1.5 font-medium text-slate-700 transition hover:bg-slate-50 disabled:cursor-not-allowed disabled:opacity-40"
            >
              Previous
            </button>
            <span className="px-1 text-slate-500">
              Page {page + 1} of {totalPages}
            </span>
            <button
              type="button"
              onClick={() => onPageChange(page + 1)}
              disabled={page >= totalPages - 1}
              className="rounded-lg border border-slate-300 px-3 py-1.5 font-medium text-slate-700 transition hover:bg-slate-50 disabled:cursor-not-allowed disabled:opacity-40"
            >
              Next
            </button>
          </div>
        </div>
      )}
    </div>
  )
}
