package dev.anand.claudeskills.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import dev.anand.claudeskills.entity.Task;
import dev.anand.claudeskills.repository.TaskRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.api.io.TempDir;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class TaskImportProcessorTest {

    @Mock
    private TaskRepository taskRepository;

    @TempDir
    Path tempDir;

    private TaskImportProcessor processor;
    private final ObjectMapper objectMapper = new ObjectMapper()
            .findAndRegisterModules()
            .disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS);

    @BeforeEach
    void setUp() {
        processor = new TaskImportProcessor(taskRepository, objectMapper);
    }

    private ImportJob runSync(String json) throws IOException {
        return runSync("tasks.json", json);
    }

    private ImportJob runSync(String filename, String json) throws IOException {
        Path file = tempDir.resolve(filename);
        Files.writeString(file, json);
        ImportJob job = new ImportJob("anand");
        processor.process("anand", job, file);
        return job;
    }

    // --- happy paths ---

    @Test
    void process_parsesPlainArray() throws IOException {
        when(taskRepository.saveAll(anyList())).thenAnswer(inv -> inv.getArgument(0));

        ImportJob job = runSync("""
                [
                  {"title":"Buy milk","status":"TODO"},
                  {"title":"Walk dog","status":"IN_PROGRESS","description":"Daily walk"}
                ]
                """);

        assertThat(job.toStatus().imported()).isEqualTo(2);
        assertThat(job.toStatus().failed()).isEqualTo(0);
        assertThat(job.toStatus().state()).isEqualTo("COMPLETED");
    }

    @Test
    void process_parsesEnvelopeFormat() throws IOException {
        when(taskRepository.saveAll(anyList())).thenAnswer(inv -> inv.getArgument(0));
        String json = objectMapper.writeValueAsString(Map.of(
                "version", 1,
                "exportedAt", Instant.now().toString(),
                "count", 1,
                "tasks", List.of(Map.of("title", "Wrapped task", "status", "DONE"))
        ));

        ImportJob job = runSync(json);

        assertThat(job.toStatus().imported()).isEqualTo(1);
        assertThat(job.toStatus().state()).isEqualTo("COMPLETED");
    }

    // --- validation ---

    @Test
    void process_skipsRowsWithMissingTitle() throws IOException {
        when(taskRepository.saveAll(anyList())).thenAnswer(inv -> inv.getArgument(0));

        ImportJob job = runSync("""
                [
                  {"status":"TODO"},
                  {"title":"Valid","status":"DONE"}
                ]
                """);

        assertThat(job.toStatus().imported()).isEqualTo(1);
        assertThat(job.toStatus().failed()).isEqualTo(1);
        assertThat(job.toStatus().state()).isEqualTo("COMPLETED_WITH_ERRORS");
    }

    @Test
    void process_skipsRowsWithInvalidStatus() throws IOException {
        ImportJob job = runSync("[{\"title\":\"Task\",\"status\":\"INVALID\"}]");

        verify(taskRepository, never()).saveAll(anyList());
        assertThat(job.toStatus().failed()).isEqualTo(1);
        assertThat(job.toStatus().imported()).isEqualTo(0);
        assertThat(job.toStatus().state()).isEqualTo("COMPLETED_WITH_ERRORS");
    }

    @Test
    void process_skipsRowsWithTitleTooLong() throws IOException {
        String title = "x".repeat(101);
        ImportJob job = runSync("[{\"title\":\"" + title + "\",\"status\":\"TODO\"}]");

        verify(taskRepository, never()).saveAll(anyList());
        assertThat(job.toStatus().failed()).isEqualTo(1);
        assertThat(job.toStatus().imported()).isEqualTo(0);
    }

    @Test
    void process_allInvalidNothingSaved() throws IOException {
        ImportJob job = runSync("[{\"status\":\"TODO\"},{\"status\":\"TODO\"}]");

        verify(taskRepository, never()).saveAll(anyList());
        assertThat(job.toStatus().imported()).isEqualTo(0);
        assertThat(job.toStatus().failed()).isEqualTo(2);
    }

    @Test
    void process_savedTasksHaveNoId() throws IOException {
        // id present in the import file must be stripped so the DB assigns a fresh one
        when(taskRepository.saveAll(anyList())).thenAnswer(inv -> inv.getArgument(0));

        runSync("[{\"id\":99,\"title\":\"T\",\"status\":\"TODO\"}]");

        // Verify saveAll was called with a list containing a task whose id is null
        verify(taskRepository).saveAll(argThat((List<Task> list) ->
                list.size() == 1 && list.get(0).getId() == null));
    }

    // --- error handling ---

    @Test
    void process_marksFailedOnBrokenJson() throws IOException {
        ImportJob job = runSync("NOT_JSON");

        assertThat(job.toStatus().state()).isEqualTo("FAILED");
    }

    @Test
    void process_deletesTheTempFileAfterCompletion() throws IOException {
        when(taskRepository.saveAll(anyList())).thenAnswer(inv -> inv.getArgument(0));
        Path file = tempDir.resolve("del.json");
        Files.writeString(file, "[{\"title\":\"T\",\"status\":\"TODO\"}]");
        ImportJob job = new ImportJob("anand");

        processor.process("anand", job, file);

        assertThat(Files.exists(file)).isFalse();
    }
}
