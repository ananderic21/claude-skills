package dev.anand.claudeskills.repository;

import dev.anand.claudeskills.entity.Task;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface TaskRepository extends JpaRepository<Task, Long> {

    Page<Task> findByStatus(String status, Pageable pageable);

    List<Task> findByStatusOrderById(String status);

    long countByStatus(String status);

    /**
     * Search tasks by an optional keyword (matched case-insensitively against
     * title or description) and an optional exact status. A null parameter means
     * "ignore this filter", so any combination of {@code q}/{@code status} works.
     */
    @Query("""
            SELECT t FROM Task t
            WHERE (:status IS NULL OR t.status = :status)
              AND (:q IS NULL
                   OR LOWER(t.title) LIKE LOWER(CONCAT('%', :q, '%'))
                   OR LOWER(t.description) LIKE LOWER(CONCAT('%', :q, '%')))
            """)
    Page<Task> search(@Param("q") String q,
                       @Param("status") String status,
                       Pageable pageable);
}