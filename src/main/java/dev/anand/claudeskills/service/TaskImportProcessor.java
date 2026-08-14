package dev.anand.claudeskills.service;

import com.fasterxml.jackson.core.JsonFactory;
import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.core.JsonToken;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import dev.anand.claudeskills.entity.Task;
import dev.anand.claudeskills.repository.TaskRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;

/**
 * Runs an import on the dedicated import executor so uploads up to 100MB never
 * block HTTP request threads. The file is parsed with Jackson's streaming API
 * (one record in memory at a time) and rows are inserted in batches, so memory
 * use stays flat regardless of file size.
 */
@Component
public class TaskImportProcessor {

    private static final Logger log = LoggerFactory.getLogger(TaskImportProcessor.class);
    private static final Logger auditLog = LoggerFactory.getLogger("AUDIT");

    private static final Set<String> VALID_STATUSES = Set.of("TODO", "IN_PROGRESS", "DONE");
    private static final int BATCH_SIZE = 500;
    private static final int MAX_LOGGED_ROW_ERRORS = 20;

    private final TaskRepository taskRepository;
    private final ObjectMapper objectMapper;

    public TaskImportProcessor(TaskRepository taskRepository, ObjectMapper objectMapper) {
        this.taskRepository = taskRepository;
        this.objectMapper = objectMapper;
    }

    @Async("importExecutor")
    public void process(String username, ImportJob job, Path file) {
        // Correlate all async log lines with the job (the request MDC does not
        // propagate to the import executor thread)
        MDC.put("requestId", job.getId().substring(0, 8));
        job.markRunning();
        try {
            parseAndSave(job, file);
            job.markFinished();
            auditLog.info("action=importTasks | actor={} | outcome=SUCCESS | details=[jobId={}, imported={}, failed={}]",
                    username, job.getId(), job.toStatus().imported(), job.toStatus().failed());
        } catch (Exception ex) {
            job.markFailed(readableReason(ex));
            auditLog.warn("action=importTasks | actor={} | outcome=FAILURE | reason={} | details=[jobId={}]",
                    username, readableReason(ex), job.getId());
        } finally {
            deleteQuietly(file);
            MDC.remove("requestId");
        }
    }

    private void parseAndSave(ImportJob job, Path file) throws IOException {
        JsonFactory factory = objectMapper.getFactory();
        try (JsonParser parser = factory.createParser(Files.newInputStream(file))) {
            JsonToken first = parser.nextToken();
            if (first == JsonToken.START_OBJECT) {
                advanceToTasksArray(parser);
            } else if (first != JsonToken.START_ARRAY) {
                throw new IOException("Expected a JSON array of tasks or an export file with a \"tasks\" array");
            }

            List<Task> batch = new ArrayList<>(BATCH_SIZE);
            long rowNumber = 0;
            while (parser.nextToken() == JsonToken.START_OBJECT) {
                rowNumber++;
                JsonNode node = parser.readValueAsTree();
                Task task = toValidTask(node, rowNumber, job);
                if (task != null) {
                    batch.add(task);
                }
                if (batch.size() >= BATCH_SIZE) {
                    saveBatch(job, batch);
                }
            }
            saveBatch(job, batch);
        }
    }

    // Skips ahead until the parser sits on the START_ARRAY of the "tasks" field
    private void advanceToTasksArray(JsonParser parser) throws IOException {
        while (parser.nextToken() != null) {
            if (parser.currentToken() == JsonToken.FIELD_NAME && "tasks".equals(parser.currentName())) {
                if (parser.nextToken() != JsonToken.START_ARRAY) {
                    throw new IOException("The \"tasks\" field must be a JSON array");
                }
                return;
            }
            // Don't descend into nested structures while looking for "tasks"
            if (parser.currentToken() == JsonToken.START_OBJECT
                    || parser.currentToken() == JsonToken.START_ARRAY) {
                parser.skipChildren();
            }
        }
        throw new IOException("No \"tasks\" array found in the import file");
    }

    private Task toValidTask(JsonNode node, long rowNumber, ImportJob job) {
        String title = textOrNull(node, "title");
        String description = textOrNull(node, "description");
        String status = textOrNull(node, "status");

        String problem = null;
        if (title == null || title.isBlank()) {
            problem = "title is required";
        } else if (title.length() > 100) {
            problem = "title is longer than 100 characters";
        } else if (description != null && description.length() > 500) {
            problem = "description is longer than 500 characters";
        } else if (status == null || !VALID_STATUSES.contains(status)) {
            problem = "status must be TODO, IN_PROGRESS or DONE";
        }

        if (problem != null) {
            job.recordFailed(1);
            if (job.toStatus().failed() <= MAX_LOGGED_ROW_ERRORS) {
                log.warn("Import row {} skipped: {}", rowNumber, problem);
            }
            return null;
        }
        return Task.builder()
                .title(title.trim())
                .description(description)
                .status(status)
                .build();
    }

    private void saveBatch(ImportJob job, List<Task> batch) {
        if (batch.isEmpty()) {
            return;
        }
        taskRepository.saveAll(List.copyOf(batch));
        job.recordImported(batch.size());
        batch.clear();
    }

    private String textOrNull(JsonNode node, String field) {
        JsonNode value = node.get(field);
        return value == null || value.isNull() ? null : value.asText();
    }

    private String readableReason(Exception ex) {
        String message = ex.getMessage();
        return message == null || message.isBlank() ? ex.getClass().getSimpleName() : message;
    }

    private void deleteQuietly(Path file) {
        try {
            Files.deleteIfExists(file);
        } catch (IOException e) {
            log.warn("Could not delete temp import file {}", file, e);
        }
    }
}
