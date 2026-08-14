package dev.anand.claudeskills.service;

import dev.anand.claudeskills.entity.Task;
import dev.anand.claudeskills.exception.TaskNotFoundException;
import dev.anand.claudeskills.repository.TaskRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
@RequiredArgsConstructor
public class TaskServiceImpl implements TaskService {

    private final TaskRepository taskRepository;

    @Override
    @Transactional(readOnly = true)
    public List<Task> getAllTasks() {
        return taskRepository.findAll();
    }

    @Override
    @Transactional(readOnly = true)
    public Task getTaskById(Long id) {
        return taskRepository.findById(id)
                .orElseThrow(() -> new TaskNotFoundException(id));
    }

    @Override
    @Transactional
    public Task createTask(Task task) {
        task.setId(null);
        return taskRepository.save(task);
    }

    @Override
    @Transactional
    public Task updateTask(Long id, Task task) {
        Task existing = getTaskById(id);
        existing.setTitle(task.getTitle());
        existing.setDescription(task.getDescription());
        existing.setStatus(task.getStatus());
        return taskRepository.save(existing);
    }

    @Override
    @Transactional
    public void deleteTask(Long id) {
        if (!taskRepository.existsById(id)) {
            throw new TaskNotFoundException(id);
        }
        taskRepository.deleteById(id);
    }
}