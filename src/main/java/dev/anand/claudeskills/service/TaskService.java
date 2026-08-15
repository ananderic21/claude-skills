package dev.anand.claudeskills.service;

import dev.anand.claudeskills.dto.TaskPageResponse;
import dev.anand.claudeskills.entity.Task;

import java.util.List;

public interface TaskService {

    List<Task> getAllTasks();

    TaskPageResponse getTasks(String status, int page, int size);

    TaskPageResponse searchTasks(String q, String status, int page, int size);

    Task getTaskById(Long id);

    Task createTask(Task task);

    Task updateTask(Long id, Task task);

    void deleteTask(Long id);
}
