package dev.anand.claudeskills.repository;

import dev.anand.claudeskills.TestcontainersConfiguration;
import dev.anand.claudeskills.entity.Task;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.boot.jdbc.test.autoconfigure.AutoConfigureTestDatabase;
import org.springframework.context.annotation.Import;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Exercises {@link TaskRepository#search} against a real MySQL (Testcontainers),
 * so it covers the actual JPQL — case-insensitive title/description matching,
 * combining with the status filter, and treating null/blank filters as "no
 * filter". Requires Docker; runs on developer machines and CI.
 */
@DataJpaTest
@Import(TestcontainersConfiguration.class)
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
class TaskRepositorySearchTest {

    private static final Pageable FIRST_PAGE = PageRequest.of(0, 10, Sort.by("id").ascending());

    @Autowired
    private TaskRepository taskRepository;

    @BeforeEach
    void seed() {
        taskRepository.deleteAll();
        taskRepository.saveAll(List.of(
                Task.builder().title("Ship the release").description("Cut the tag and deploy").status("TODO").build(),
                Task.builder().title("Write docs").description("Document the RELEASE process").status("IN_PROGRESS").build(),
                Task.builder().title("Fix login bug").description("Token refresh fails").status("TODO").build(),
                Task.builder().title("Plan sprint").description(null).status("DONE").build()
        ));
    }

    @Test
    void search_matchesTitleCaseInsensitively() {
        Page<Task> result = taskRepository.search("ship", null, FIRST_PAGE);

        assertThat(result.getContent())
                .extracting(Task::getTitle)
                .containsExactly("Ship the release");
    }

    @Test
    void search_matchesDescriptionCaseInsensitively() {
        // "release" appears in one title and one (upper-cased) description.
        Page<Task> result = taskRepository.search("release", null, FIRST_PAGE);

        assertThat(result.getContent())
                .extracting(Task::getTitle)
                .containsExactlyInAnyOrder("Ship the release", "Write docs");
    }

    @Test
    void search_combinesKeywordWithStatus() {
        Page<Task> result = taskRepository.search("release", "TODO", FIRST_PAGE);

        assertThat(result.getContent())
                .extracting(Task::getTitle)
                .containsExactly("Ship the release");
    }

    @Test
    void search_nullKeyword_filtersByStatusOnly() {
        Page<Task> result = taskRepository.search(null, "TODO", FIRST_PAGE);

        assertThat(result.getContent())
                .extracting(Task::getTitle)
                .containsExactlyInAnyOrder("Ship the release", "Fix login bug");
    }

    @Test
    void search_nullKeywordAndNullStatus_returnsEverything() {
        Page<Task> result = taskRepository.search(null, null, FIRST_PAGE);

        assertThat(result.getTotalElements()).isEqualTo(4);
    }

    @Test
    void search_handlesTasksWithNullDescription_withoutError() {
        // "sprint" only matches a title whose description is null — the OR on a
        // null description column must not blow up or accidentally exclude it.
        Page<Task> result = taskRepository.search("sprint", null, FIRST_PAGE);

        assertThat(result.getContent())
                .extracting(Task::getTitle)
                .containsExactly("Plan sprint");
    }

    @Test
    void search_noMatch_returnsEmptyPage() {
        Page<Task> result = taskRepository.search("nonexistent-keyword", null, FIRST_PAGE);

        assertThat(result.getContent()).isEmpty();
        assertThat(result.getTotalElements()).isZero();
    }

    @Test
    void search_respectsPaging() {
        Page<Task> firstPage = taskRepository.search(null, null, PageRequest.of(0, 2, Sort.by("id").ascending()));

        assertThat(firstPage.getContent()).hasSize(2);
        assertThat(firstPage.getTotalElements()).isEqualTo(4);
        assertThat(firstPage.getTotalPages()).isEqualTo(2);
    }
}
