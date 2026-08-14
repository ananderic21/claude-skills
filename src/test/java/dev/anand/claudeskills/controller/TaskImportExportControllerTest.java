package dev.anand.claudeskills.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import dev.anand.claudeskills.dto.ImportJobStatus;
import dev.anand.claudeskills.dto.TaskExportFile;
import dev.anand.claudeskills.dto.TaskExportRequest;
import dev.anand.claudeskills.entity.Task;
import dev.anand.claudeskills.exception.GlobalExceptionHandler;
import dev.anand.claudeskills.exception.InvalidFileException;
import dev.anand.claudeskills.exception.ResourceNotFoundException;
import dev.anand.claudeskills.service.TaskImportExportService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.time.Instant;
import java.util.List;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@ExtendWith(MockitoExtension.class)
class TaskImportExportControllerTest {

    @Mock
    private TaskImportExportService importExportService;

    @InjectMocks
    private TaskImportExportController controller;

    private MockMvc mockMvc;

    private final ObjectMapper objectMapper = new ObjectMapper().findAndRegisterModules();

    private final UsernamePasswordAuthenticationToken principal =
            new UsernamePasswordAuthenticationToken("anand", null);

    private static ImportJobStatus pendingJob(String id) {
        return new ImportJobStatus(id, "PENDING", 0, 0, 0, null, Instant.now(), null);
    }

    @BeforeEach
    void setUp() {
        mockMvc = MockMvcBuilders.standaloneSetup(controller)
                .setControllerAdvice(new GlobalExceptionHandler())
                .build();
    }

    // --- export ---

    @Test
    void exportTasks_returns200WithAttachment() throws Exception {
        Task task = Task.builder().id(1L).title("Test").status("TODO").build();
        TaskExportFile file = TaskExportFile.of(List.of(task));
        when(importExportService.exportTasks(List.of(1L))).thenReturn(file);

        mockMvc.perform(post("/api/tasks/export")
                        .principal(principal)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new TaskExportRequest(List.of(1L)))))
                .andExpect(status().isOk())
                .andExpect(header().string("Content-Disposition", org.hamcrest.Matchers.containsString("attachment")))
                .andExpect(jsonPath("$.version").value(1))
                .andExpect(jsonPath("$.count").value(1))
                .andExpect(jsonPath("$.tasks[0].title").value("Test"));
    }

    @Test
    void exportTasks_emptyIdListFails400() throws Exception {
        mockMvc.perform(post("/api/tasks/export")
                        .principal(principal)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new TaskExportRequest(List.of()))))
                .andExpect(status().isBadRequest());
    }

    // --- import ---

    @Test
    void importTasks_returns202WithJobId() throws Exception {
        MockMultipartFile upload = new MockMultipartFile("file", "tasks.json",
                "application/json", "[{\"title\":\"T\",\"status\":\"TODO\"}]".getBytes());
        when(importExportService.startImport(eq("anand"), any())).thenReturn(pendingJob("job-1"));

        mockMvc.perform(multipart("/api/tasks/import")
                        .file(upload)
                        .principal(principal))
                .andExpect(status().isAccepted())
                .andExpect(jsonPath("$.jobId").value("job-1"))
                .andExpect(jsonPath("$.state").value("PENDING"));
    }

    @Test
    void importTasks_serviceThrowsInvalidFile_returns400() throws Exception {
        MockMultipartFile upload = new MockMultipartFile("file", "tasks.csv",
                "text/csv", "col1,col2".getBytes());
        when(importExportService.startImport(any(), any()))
                .thenThrow(new InvalidFileException("Only JSON files are supported"));

        mockMvc.perform(multipart("/api/tasks/import")
                        .file(upload)
                        .principal(principal))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error").value("Only JSON files are supported"));
    }

    // --- status ---

    @Test
    void getImportStatus_returnsJobStatus() throws Exception {
        when(importExportService.getImportStatus("job-1"))
                .thenReturn(pendingJob("job-1"));

        mockMvc.perform(get("/api/tasks/import/job-1").principal(principal))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.jobId").value("job-1"))
                .andExpect(jsonPath("$.state").value("PENDING"));
    }

    @Test
    void getImportStatus_unknownJobReturns404() throws Exception {
        when(importExportService.getImportStatus("no-such-job"))
                .thenThrow(new ResourceNotFoundException("Import job not found: no-such-job"));

        mockMvc.perform(get("/api/tasks/import/no-such-job").principal(principal))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.error").value("Import job not found: no-such-job"));
    }
}
