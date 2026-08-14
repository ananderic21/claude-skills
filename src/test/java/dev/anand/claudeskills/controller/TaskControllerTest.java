package dev.anand.claudeskills.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import dev.anand.claudeskills.dto.TaskPageResponse;
import dev.anand.claudeskills.entity.Task;
import dev.anand.claudeskills.exception.GlobalExceptionHandler;
import dev.anand.claudeskills.exception.TaskNotFoundException;
import dev.anand.claudeskills.service.TaskService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.util.List;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@ExtendWith(MockitoExtension.class)
class TaskControllerTest {

    @Mock
    private TaskService taskService;

    @InjectMocks
    private TaskController taskController;

    private MockMvc mockMvc;

    private final ObjectMapper objectMapper = new ObjectMapper();

    private Task task;

    @BeforeEach
    void setUp() {
        mockMvc = MockMvcBuilders.standaloneSetup(taskController)
                .setControllerAdvice(new GlobalExceptionHandler())
                .build();
        task = Task.builder()
                .id(1L)
                .title("Write tests")
                .description("Controller tests with MockMvc")
                .status("TODO")
                .build();
    }

    @Test
    void getAllTasks_returns200WithTaskList() throws Exception {
        when(taskService.getAllTasks()).thenReturn(List.of(task));

        mockMvc.perform(get("/api/tasks"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(1))
                .andExpect(jsonPath("$[0].id").value(1))
                .andExpect(jsonPath("$[0].title").value("Write tests"))
                .andExpect(jsonPath("$[0].status").value("TODO"));
    }

    @Test
    void getTasksPage_passesFilterAndPagingToService() throws Exception {
        TaskPageResponse pageResponse = new TaskPageResponse(
                List.of(task), 0, 10, 1, 1, 4, 2, 3);
        when(taskService.getTasks(eq("TODO"), eq(0), eq(10))).thenReturn(pageResponse);

        mockMvc.perform(get("/api/tasks/page")
                        .param("status", "TODO")
                        .param("page", "0")
                        .param("size", "10"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.tasks.length()").value(1))
                .andExpect(jsonPath("$.totalElements").value(1))
                .andExpect(jsonPath("$.todoCount").value(4))
                .andExpect(jsonPath("$.doneCount").value(3));

        verify(taskService).getTasks("TODO", 0, 10);
    }

    @Test
    void getTasksPage_withoutParams_usesDefaults() throws Exception {
        when(taskService.getTasks(null, 0, 10))
                .thenReturn(new TaskPageResponse(List.of(), 0, 10, 0, 0, 0, 0, 0));

        mockMvc.perform(get("/api/tasks/page"))
                .andExpect(status().isOk());

        verify(taskService).getTasks(null, 0, 10);
    }

    @Test
    void getTaskById_whenTaskExists_returns200WithTask() throws Exception {
        when(taskService.getTaskById(1L)).thenReturn(task);

        mockMvc.perform(get("/api/tasks/1"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(1))
                .andExpect(jsonPath("$.title").value("Write tests"));
    }

    @Test
    void getTaskById_whenTaskMissing_returns404WithErrorBody() throws Exception {
        when(taskService.getTaskById(99L)).thenThrow(new TaskNotFoundException(99L));

        mockMvc.perform(get("/api/tasks/99"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.error").value("Task not found with id: 99"));
    }

    @Test
    void createTask_withValidBody_returns201WithCreatedTask() throws Exception {
        Task saved = Task.builder().id(5L).title("New task").description("").status("TODO").build();
        when(taskService.createTask(any(Task.class))).thenReturn(saved);

        Task payload = Task.builder().title("New task").description("").status("TODO").build();

        mockMvc.perform(post("/api/tasks")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(payload)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.id").value(5))
                .andExpect(jsonPath("$.title").value("New task"));
    }

    @Test
    void createTask_withBlankTitle_returns400AndNeverCallsService() throws Exception {
        Task payload = Task.builder().title("").status("TODO").build();

        mockMvc.perform(post("/api/tasks")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(payload)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.title").value("Title is required"));

        verify(taskService, never()).createTask(any(Task.class));
    }

    @Test
    void createTask_withInvalidStatus_returns400() throws Exception {
        Task payload = Task.builder().title("Valid title").status("INVALID").build();

        mockMvc.perform(post("/api/tasks")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(payload)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.status").value("Status must be TODO, IN_PROGRESS or DONE"));
    }

    @Test
    void updateTask_withValidBody_returns200WithUpdatedTask() throws Exception {
        Task updated = Task.builder().id(1L).title("Write tests").description("done").status("DONE").build();
        when(taskService.updateTask(eq(1L), any(Task.class))).thenReturn(updated);

        Task payload = Task.builder().title("Write tests").description("done").status("DONE").build();

        mockMvc.perform(put("/api/tasks/1")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(payload)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("DONE"));
    }

    @Test
    void deleteTask_whenTaskExists_returns204() throws Exception {
        mockMvc.perform(delete("/api/tasks/1"))
                .andExpect(status().isNoContent());

        verify(taskService).deleteTask(1L);
    }

    @Test
    void deleteTask_whenTaskMissing_returns404() throws Exception {
        doThrow(new TaskNotFoundException(99L)).when(taskService).deleteTask(99L);

        mockMvc.perform(delete("/api/tasks/99"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.error").value("Task not found with id: 99"));
    }
}
