package dev.anand.claudeskills.repository;

import dev.anand.claudeskills.entity.Task;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface TaskRepository extends JpaRepository<Task, Long> {

    Page<Task> findByStatus(String status, Pageable pageable);

    List<Task> findByStatusOrderById(String status);

    long countByStatus(String status);
}