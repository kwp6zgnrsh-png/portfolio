package com.study.backend.file.cleanup.model;

import java.time.LocalDateTime;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Getter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class FileCleanupTask {

	public enum TaskType {
		MOVE,
		DELETE
	}

	private Long id;
	private TaskType taskType;
	private String sourcePath;
	private String destinationPath;
	private int attempts;
	private LocalDateTime nextAttemptAt;
	private String lastError;
	private LocalDateTime createdAt;
}
