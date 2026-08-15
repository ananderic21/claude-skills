package dev.anand.claudeskills.service;

import dev.anand.claudeskills.dto.TaskPageResponse;
import dev.anand.claudeskills.entity.Task;
import dev.anand.claudeskills.exception.TaskNotFoundException;
import dev.anand.claudeskills.repository.TaskRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;

import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class TaskServiceImplTest {

    @Mock
    private TaskRepository taskRepository;

    @InjectMocks
    private TaskServiceImpl taskService;

    private Task task;

    @BeforeEach
    void setUp() {
        task = Task.builder()
                .id(1L)
                .title("Write tests")
                .description("Unit tests with Mockito")
                .status("TODO")
                .build();
    }

    @Test
    void getAllTasks_returnsAllTasksFromRepository() {
        when(taskRepository.findAll()).thenReturn(List.of(task));

        List<Task> result = taskService.getAllTasks();

        assertEquals(1, result.size());
        assertEquals("Write tests", result.get(0).getTitle());
        verify(taskRepository).findAll();
    }

    @Test
    void getTasks_withoutStatus_usesFindAllAndReturnsGlobalCounts() {
        when(taskRepository.findAll(any(Pageable.class)))
                .thenReturn(new PageImpl<>(List.of(task), PageRequest.of(0, 10), 1));
        when(taskRepository.countByStatus("TODO")).thenReturn(5L);
        when(taskRepository.countByStatus("IN_PROGRESS")).thenReturn(2L);
        when(taskRepository.countByStatus("DONE")).thenReturn(3L);

        TaskPageResponse response = taskService.getTasks(null, 0, 10);

        assertEquals(1, response.tasks().size());
        assertEquals(0, response.page());
        assertEquals(1, response.totalElements());
        assertEquals(5L, response.todoCount());
        assertEquals(2L, response.inProgressCount());
        assertEquals(3L, response.doneCount());
        verify(taskRepository).findAll(any(Pageable.class));
        verify(taskRepository, never()).findByStatus(any(), any());
    }

    @Test
    void getTasks_withStatus_filtersByStatus() {
        when(taskRepository.findByStatus(eq("DONE"), any(Pageable.class)))
                .thenReturn(new PageImpl<>(List.of(task), PageRequest.of(0, 10), 1));

        TaskPageResponse response = taskService.getTasks("DONE", 0, 10);

        assertEquals(1, response.tasks().size());
        verify(taskRepository).findByStatus(eq("DONE"), any(Pageable.class));
        verify(taskRepository, never()).findAll(any(Pageable.class));
    }

    @Test
    void getTasks_clampsOversizedPageSizeTo100() {
        ArgumentCaptor<Pageable> captor = ArgumentCaptor.forClass(Pageable.class);
        when(taskRepository.findAll(captor.capture()))
                .thenReturn(new PageImpl<>(List.of(), PageRequest.of(0, 100), 0));

        taskService.getTasks(null, 0, 9999);

        assertEquals(100, captor.getValue().getPageSize());
    }

    @Test
    void searchTasks_passesKeywordAndStatusToRepository_andReturnsGlobalCounts() {
        when(taskRepository.search(eq("report"), eq("TODO"), any(Pageable.class)))
                .thenReturn(new PageImpl<>(List.of(task), PageRequest.of(0, 10), 1));
        when(taskRepository.countByStatus("TODO")).thenReturn(5L);
        when(taskRepository.countByStatus("IN_PROGRESS")).thenReturn(2L);
        when(taskRepository.countByStatus("DONE")).thenReturn(3L);

        TaskPageResponse response = taskService.searchTasks("report", "TODO", 0, 10);

        assertEquals(1, response.tasks().size());
        assertEquals(1, response.totalElements());
        assertEquals(5L, response.todoCount());
        assertEquals(2L, response.inProgressCount());
        assertEquals(3L, response.doneCount());
        verify(taskRepository).search(eq("report"), eq("TODO"), any(Pageable.class));
    }

    @Test
    void searchTasks_trimsKeyword_andNormalizesBlankFiltersToNull() {
        ArgumentCaptor<String> qCaptor = ArgumentCaptor.forClass(String.class);
        ArgumentCaptor<String> statusCaptor = ArgumentCaptor.forClass(String.class);
        when(taskRepository.search(qCaptor.capture(), statusCaptor.capture(), any(Pageable.class)))
                .thenReturn(new PageImpl<>(List.of(task), PageRequest.of(0, 10), 1));

        taskService.searchTasks("  report  ", "   ", 0, 10);

        assertEquals("report", qCaptor.getValue());
        assertNull(statusCaptor.getValue());
    }

    @Test
    void searchTasks_withAllBlankFilters_passesNullKeywordAndStatus() {
        ArgumentCaptor<String> qCaptor = ArgumentCaptor.forClass(String.class);
        ArgumentCaptor<String> statusCaptor = ArgumentCaptor.forClass(String.class);
        when(taskRepository.search(qCaptor.capture(), statusCaptor.capture(), any(Pageable.class)))
                .thenReturn(new PageImpl<>(List.of(), PageRequest.of(0, 10), 0));

        taskService.searchTasks(null, "", 0, 10);

        assertNull(qCaptor.getValue());
        assertNull(statusCaptor.getValue());
    }

    @Test
    void searchTasks_clampsOversizedPageSizeTo100_andNegativePageToZero() {
        ArgumentCaptor<Pageable> captor = ArgumentCaptor.forClass(Pageable.class);
        when(taskRepository.search(any(), any(), captor.capture()))
                .thenReturn(new PageImpl<>(List.of(), PageRequest.of(0, 100), 0));

        taskService.searchTasks("q", "TODO", -3, 9999);

        assertEquals(100, captor.getValue().getPageSize());
        assertEquals(0, captor.getValue().getPageNumber());
    }

    @Test
    void getTaskById_whenTaskExists_returnsTask() {
        when(taskRepository.findById(1L)).thenReturn(Optional.of(task));

        Task result = taskService.getTaskById(1L);

        assertEquals(1L, result.getId());
        assertEquals("Write tests", result.getTitle());
    }

    @Test
    void getTaskById_whenTaskMissing_throwsTaskNotFoundException() {
        when(taskRepository.findById(99L)).thenReturn(Optional.empty());

        TaskNotFoundException ex =
                assertThrows(TaskNotFoundException.class, () -> taskService.getTaskById(99L));

        assertEquals("Task not found with id: 99", ex.getMessage());
    }

    @Test
    void createTask_ignoresClientProvidedId_andSavesTask() {
        Task incoming = Task.builder()
                .id(42L)
                .title("New task")
                .status("TODO")
                .build();
        when(taskRepository.save(any(Task.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));

        taskService.createTask(incoming);

        ArgumentCaptor<Task> captor = ArgumentCaptor.forClass(Task.class);
        verify(taskRepository).save(captor.capture());
        assertNull(captor.getValue().getId());
        assertEquals("New task", captor.getValue().getTitle());
    }

    @Test
    void updateTask_whenTaskExists_updatesFieldsAndSaves() {
        Task changes = Task.builder()
                .title("Updated title")
                .description("Updated description")
                .status("DONE")
                .build();
        when(taskRepository.findById(1L)).thenReturn(Optional.of(task));
        when(taskRepository.save(any(Task.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));

        Task result = taskService.updateTask(1L, changes);

        assertEquals(1L, result.getId());
        assertEquals("Updated title", result.getTitle());
        assertEquals("Updated description", result.getDescription());
        assertEquals("DONE", result.getStatus());
    }

    @Test
    void updateTask_whenTaskMissing_throwsAndDoesNotSave() {
        when(taskRepository.findById(99L)).thenReturn(Optional.empty());

        assertThrows(TaskNotFoundException.class, () -> taskService.updateTask(99L, task));

        verify(taskRepository, never()).save(any(Task.class));
    }

    @Test
    void deleteTask_whenTaskExists_deletesById() {
        when(taskRepository.existsById(1L)).thenReturn(true);

        taskService.deleteTask(1L);

        verify(taskRepository).deleteById(1L);
    }

    @Test
    void deleteTask_whenTaskMissing_throwsAndDoesNotDelete() {
        when(taskRepository.existsById(99L)).thenReturn(false);

        assertThrows(TaskNotFoundException.class, () -> taskService.deleteTask(99L));

        verify(taskRepository, never()).deleteById(any());
    }
}
