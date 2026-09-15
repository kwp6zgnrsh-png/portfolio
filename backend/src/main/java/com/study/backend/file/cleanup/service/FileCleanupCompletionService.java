package com.study.backend.file.cleanup.service;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.study.backend.file.cleanup.mapper.FileCleanupTaskMapper;
import com.study.backend.file.cleanup.model.FileCleanupTask;

@Service
public class FileCleanupCompletionService {

	private final FileCleanupTaskMapper taskMapper;
	private final int retentionDays;

	public FileCleanupCompletionService(FileCleanupTaskMapper taskMapper,
										@Value("${file.orphan.retention-days:7}") int retentionDays) {
		if (retentionDays < 1) {
			throw new IllegalArgumentException("파일 보관 기간은 1일 이상이어야 합니다.");
		}

		this.taskMapper = taskMapper;
		this.retentionDays = retentionDays;
	}

	/**
	 * 물리 작업 성공 후 DB 작업을 완료한다.
	 * MOVE이면 기존 작업 제거와 지연 DELETE 등록을 함께 커밋한다.
	 */
	@Transactional
	public void complete(FileCleanupTask task) {
		if (task.getId() == null || task.getTaskType() == null) {
			throw new IllegalArgumentException("완료할 파일 정리 작업 정보가 유효하지 않습니다.");
		}

		if (task.getTaskType() == FileCleanupTask.TaskType.MOVE
			&& (task.getDestinationPath() == null
			|| task.getDestinationPath().isBlank())) {

			throw new IllegalArgumentException("이동 완료된 파일의 목적지 경로가 없습니다.");
		}

		int deletedRows = taskMapper.deleteTask(task.getId());

		// 이미 완료된 작업이면 중복으로 DELETE 작업을 등록하지 않는다.
		if (deletedRows == 0) {
			return;
		}

		if (deletedRows != 1) {
			throw new IllegalStateException("파일 정리 작업 완료 처리 건수가 올바르지 않습니다.");
		}

		if (task.getTaskType() == FileCleanupTask.TaskType.MOVE) {
			int insertedRows = taskMapper.insertDelayedDelete(
				task.getDestinationPath(),
				retentionDays
			);

			if (insertedRows != 1) {
				throw new IllegalStateException("보관 파일의 지연 삭제 작업 등록에 실패했습니다.");
			}
		}
	}
}