export type ImportJobState =
  | 'PENDING'
  | 'RUNNING'
  | 'COMPLETED'
  | 'COMPLETED_WITH_ERRORS'
  | 'FAILED'

export interface ImportJobStatus {
  jobId: string
  state: ImportJobState
  totalRecords: number
  imported: number
  failed: number
  error: string | null
  submittedAt: string
  finishedAt: string | null
}
