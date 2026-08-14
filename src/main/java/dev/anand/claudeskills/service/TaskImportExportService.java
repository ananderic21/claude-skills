package dev.anand.claudeskills.service;

import dev.anand.claudeskills.dto.ImportJobStatus;
import dev.anand.claudeskills.dto.TaskExportFile;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;

public interface TaskImportExportService {

    TaskExportFile exportTasks(List<Long> ids);

    TaskExportFile exportTasksByStatus(String status);

    ImportJobStatus startImport(String username, MultipartFile file);

    ImportJobStatus getImportStatus(String jobId);
}
