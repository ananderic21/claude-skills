package dev.anand.claudeskills.service;

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

import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
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
