package dev.anand.claudeskills.dto;

import jakarta.validation.constraints.NotEmpty;

import java.util.List;

public record TaskExportRequest(
        @NotEmpty(message = "Select at least one task to export")
        List<Long> ids) {
}
