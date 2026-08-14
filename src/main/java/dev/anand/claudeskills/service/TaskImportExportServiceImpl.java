package dev.anand.claudeskills.service;

import dev.anand.claudeskills.dto.ImportJobStatus;
import dev.anand.claudeskills.dto.TaskExportFile;
import dev.anand.claudeskills.exception.InvalidFileException;
import dev.anand.claudeskills.exception.ResourceNotFoundException;
import dev.anand.claudeskills.repository.TaskRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.RejectedExecutionException;

@Service
public class TaskImportExportServiceImpl implements TaskImportExportService {

    static final long MAX_IMPORT_BYTES = 100L * 1024 * 1024;
    private static final Duration FINISHED_JOB_RETENTION = Duration.ofHours(1);

    private final TaskRepository taskRepository;
    private final TaskImportProcessor importProcessor;
    private final ConcurrentMap<String, ImportJob> jobs = new ConcurrentHashMap<>();

    public TaskImportExportServiceImpl(TaskRepository taskRepository,
                                       TaskImportProcessor importProcessor) {
        this.taskRepository = taskRepository;
        this.importProcessor = importProcessor;
    }

    @Override
    @Transactional(readOnly = true)
    public TaskExportFile exportTasks(List<Long> ids) {
        return TaskExportFile.of(taskRepository.findAllById(ids));
    }

    @Override
    public ImportJobStatus startImport(String username, MultipartFile file) {
        validateImportFile(file);
        purgeExpiredJobs();

        // Spool the upload to a temp file so the HTTP request can return
        // immediately; the import executor owns (and deletes) the copy.
        Path tempFile;
        try {
            tempFile = Files.createTempFile("task-import-", ".json");
            file.transferTo(tempFile);
        } catch (IOException e) {
            throw new UncheckedIOException("Failed to store the import file", e);
        }

        ImportJob job = new ImportJob(username);
        jobs.put(job.getId(), job);
        try {
            importProcessor.process(username, job, tempFile);
        } catch (RejectedExecutionException e) {
            jobs.remove(job.getId());
            deleteQuietly(tempFile);
            throw e;
        }
        return job.toStatus();
    }

    @Override
    public ImportJobStatus getImportStatus(String jobId) {
        ImportJob job = jobs.get(jobId);
        if (job == null) {
            throw new ResourceNotFoundException("Import job not found: " + jobId);
        }
        return job.toStatus();
    }

    private void validateImportFile(MultipartFile file) {
        if (file == null || file.isEmpty()) {
            throw new InvalidFileException("Please select a JSON file to import");
        }
        if (file.getSize() > MAX_IMPORT_BYTES) {
            throw new InvalidFileException("Import file is too large (max 100MB)");
        }
        String filename = file.getOriginalFilename();
        boolean jsonName = filename != null && filename.toLowerCase(Locale.ROOT).endsWith(".json");
        String contentType = file.getContentType();
        boolean jsonType = contentType != null && contentType.toLowerCase(Locale.ROOT).contains("json");
        if (!jsonName && !jsonType) {
            throw new InvalidFileException("Only JSON files are supported");
        }
    }

    private void purgeExpiredJobs() {
        Instant cutoff = Instant.now().minus(FINISHED_JOB_RETENTION);
        jobs.values().removeIf(job -> job.isFinished() && job.getFinishedAt().isBefore(cutoff));
    }

    private void deleteQuietly(Path path) {
        try {
            Files.deleteIfExists(path);
        } catch (IOException ignored) {
            // Best-effort cleanup of a temp file
        }
    }
}
