package dev.anand.claudeskills.dto;

import dev.anand.claudeskills.entity.Task;

import java.util.List;

/**
 * A single page of tasks plus the global per-status counts. The counts are
 * always for the whole table (not just the current page or filter) so the
 * dashboard stat cards stay accurate regardless of paging/filtering.
 */
public record TaskPageResponse(
        List<Task> tasks,
        int page,
        int size,
        long totalElements,
        int totalPages,
        long todoCount,
        long inProgressCount,
        long doneCount
) {
}
