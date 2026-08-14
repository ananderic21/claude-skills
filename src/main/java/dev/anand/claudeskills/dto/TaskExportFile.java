package dev.anand.claudeskills.dto;

import dev.anand.claudeskills.entity.Task;

import java.time.Instant;
import java.util.List;

/**
 * Envelope written to the exported JSON file. The importer accepts either this
 * envelope or a bare JSON array of tasks; ids are ignored on import.
 */
public record TaskExportFile(
        int version,
        Instant exportedAt,
        int count,
        List<Task> tasks) {

    public static TaskExportFile of(List<Task> tasks) {
        return new TaskExportFile(1, Instant.now(), tasks.size(), tasks);
    }
}
