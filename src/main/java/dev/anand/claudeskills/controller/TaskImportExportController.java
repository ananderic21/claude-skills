package dev.anand.claudeskills.controller;

import dev.anand.claudeskills.dto.ImportJobStatus;
import dev.anand.claudeskills.dto.TaskExportFile;
import dev.anand.claudeskills.dto.TaskExportRequest;
import dev.anand.claudeskills.service.TaskImportExportService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.CrossOrigin;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import java.security.Principal;
import java.time.LocalDate;

@RestController
@RequestMapping("/api/tasks")
@CrossOrigin(origins = "http://localhost:5173")
@RequiredArgsConstructor
public class TaskImportExportController {

    private final TaskImportExportService importExportService;

    @PostMapping("/export")
    public ResponseEntity<TaskExportFile> exportTasks(@Valid @RequestBody TaskExportRequest request) {
        TaskExportFile file = importExportService.exportTasks(request.ids());
        String filename = "tasks-export-" + LocalDate.now() + ".json";
        return ResponseEntity.ok()
                .contentType(MediaType.APPLICATION_JSON)
                .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"" + filename + "\"")
                .body(file);
    }

    @PostMapping(value = "/import", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ResponseEntity<ImportJobStatus> importTasks(@RequestParam("file") MultipartFile file,
                                                      Principal principal) {
        ImportJobStatus status = importExportService.startImport(principal.getName(), file);
        return ResponseEntity.status(HttpStatus.ACCEPTED).body(status);
    }

    @GetMapping("/import/{jobId}")
    public ResponseEntity<ImportJobStatus> getImportStatus(@PathVariable String jobId) {
        return ResponseEntity.ok(importExportService.getImportStatus(jobId));
    }
}
