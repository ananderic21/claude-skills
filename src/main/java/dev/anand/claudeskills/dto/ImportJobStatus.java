package dev.anand.claudeskills.dto;

import java.time.Instant;

/**
 * Snapshot of an asynchronous import job, safe to serialize while the job is
 * still being processed on the import executor.
 */
public record ImportJobStatus(
        String jobId,
        String state,
        long totalRecords,
        long imported,
        long failed,
        String error,
        Instant submittedAt,
        Instant finishedAt) {
}
