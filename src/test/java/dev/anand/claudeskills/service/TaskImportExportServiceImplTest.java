package dev.anand.claudeskills.service;

import dev.anand.claudeskills.dto.ImportJobStatus;
import dev.anand.claudeskills.dto.TaskExportFile;
import dev.anand.claudeskills.entity.Task;
import dev.anand.claudeskills.exception.InvalidFileException;
import dev.anand.claudeskills.exception.ResourceNotFoundException;
import dev.anand.claudeskills.repository.TaskRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.mock.web.MockMultipartFile;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class TaskImportExportServiceImplTest {

    @Mock
    private TaskRepository taskRepository;

    @Mock
    private TaskImportProcessor importProcessor;

    @InjectMocks
    private TaskImportExportServiceImpl service;

    private static Task task(long id, String title, String status) {
        return Task.builder().id(id).title(title).status(status).build();
    }

    // --- export ---

    @Test
    void exportTasks_returnsTasks() {
        List<Task> tasks = List.of(task(1L, "A", "TODO"), task(2L, "B", "DONE"));
        when(taskRepository.findAllById(List.of(1L, 2L))).thenReturn(tasks);

        TaskExportFile file = service.exportTasks(List.of(1L, 2L));

        assertThat(file.tasks()).hasSize(2);
        assertThat(file.count()).isEqualTo(2);
        assertThat(file.version()).isEqualTo(1);
    }

    @Test
    void exportTasks_emptySelectionReturnsEmptyList() {
        when(taskRepository.findAllById(List.of())).thenReturn(List.of());

        TaskExportFile file = service.exportTasks(List.of());

        assertThat(file.tasks()).isEmpty();
        assertThat(file.count()).isEqualTo(0);
    }

    // --- import validation ---

    @Test
    void startImport_rejectsNullFile() {
        assertThatThrownBy(() -> service.startImport("anand", null))
                .isInstanceOf(InvalidFileException.class)
                .hasMessageContaining("select a JSON file");
    }

    @Test
    void startImport_rejectsEmptyFile() {
        MockMultipartFile file = new MockMultipartFile("file", "tasks.json",
                "application/json", new byte[0]);

        assertThatThrownBy(() -> service.startImport("anand", file))
                .isInstanceOf(InvalidFileException.class)
                .hasMessageContaining("select a JSON file");
    }

    @Test
    void startImport_rejectsNonJsonExtension() {
        MockMultipartFile file = new MockMultipartFile("file", "tasks.csv",
                "text/csv", "[{}]".getBytes());

        assertThatThrownBy(() -> service.startImport("anand", file))
                .isInstanceOf(InvalidFileException.class)
                .hasMessageContaining("JSON files are supported");
    }

    @Test
    void startImport_acceptsJsonContentTypeWithoutExtension() {
        // file named ".bin" but served as application/json — should pass validation
        byte[] content = "[{\"title\":\"T\",\"status\":\"TODO\"}]".getBytes();
        MockMultipartFile file = new MockMultipartFile("file", "tasks.bin",
                "application/json", content);

        ImportJobStatus status = service.startImport("anand", file);

        assertThat(status.jobId()).isNotBlank();
        assertThat(status.state()).isEqualTo("PENDING");
    }

    @Test
    void startImport_acceptsJsonExtensionAnyContentType() {
        byte[] content = "[{\"title\":\"T\",\"status\":\"TODO\"}]".getBytes();
        MockMultipartFile file = new MockMultipartFile("file", "tasks.json",
                "application/octet-stream", content);

        ImportJobStatus status = service.startImport("anand", file);

        assertThat(status.jobId()).isNotBlank();
        assertThat(status.state()).isEqualTo("PENDING");
    }

    // --- status ---

    @Test
    void getImportStatus_unknownJobThrowsNotFound() {
        assertThatThrownBy(() -> service.getImportStatus("no-such-job"))
                .isInstanceOf(ResourceNotFoundException.class)
                .hasMessageContaining("no-such-job");
    }

    @Test
    void getImportStatus_returnsStatusAfterSubmit() {
        byte[] content = "[{\"title\":\"T\",\"status\":\"TODO\"}]".getBytes();
        MockMultipartFile file = new MockMultipartFile("file", "tasks.json",
                "application/json", content);

        ImportJobStatus submitted = service.startImport("anand", file);
        ImportJobStatus fetched = service.getImportStatus(submitted.jobId());

        assertThat(fetched.jobId()).isEqualTo(submitted.jobId());
    }
}
