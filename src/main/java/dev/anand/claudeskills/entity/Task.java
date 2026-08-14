package dev.anand.claudeskills.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Entity
@Table(name = "tasks")
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class Task {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @NotBlank(message = "Title is required")
    @Size(max = 100, message = "Title must be at most 100 characters")
    @Column(nullable = false, length = 100)
    private String title;

    @Size(max = 500, message = "Description must be at most 500 characters")
    @Column(length = 500)
    private String description;

    @NotBlank(message = "Status is required")
    @Pattern(regexp = "TODO|IN_PROGRESS|DONE", message = "Status must be TODO, IN_PROGRESS or DONE")
    @Column(nullable = false, length = 20)
    private String status;
}