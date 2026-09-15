package com.study.backend.file.cleanup.mapper;

import java.util.List;

import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import com.study.backend.file.cleanup.model.FileCleanupTask;

@Mapper
public interface FileCleanupTaskMapper {

	int insertTask(FileCleanupTask task);

	List<FileCleanupTask> findDueTasks(@Param("limit") int limit);

	int deleteTask(@Param("taskId") Long taskId);

	int recordFailure(
		@Param("taskId") Long taskId,
		@Param("retryDelaySeconds") long retryDelaySeconds,
		@Param("lastError") String lastError
	);

	int insertDelayedDelete(
		@Param("sourcePath") String sourcePath,
		@Param("retentionDays") int retentionDays
	);
}