package com.study.backend.file.cleanup.service;

import java.nio.file.Path;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import com.study.backend.file.cleanup.mapper.FileCleanupTaskMapper;
import com.study.backend.file.cleanup.model.FileCleanupTask;
import com.study.backend.file.cleanup.model.FileCleanupTask.TaskType;

import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
public class FileCleanupTaskService {

	private static final int MAX_PATH_LENGTH = 1024;

	private final FileCleanupTaskMapper taskMapper;

	/**
	 * 기존 트랜잭션에 참여하여 파일 이동 작업을 저장한다.
	 * 물리 파일 이동은 수행하지 않는다.
	 */
	@Transactional(propagation = Propagation.MANDATORY)
	public void enqueueMove(String sourcePath, String destinationPath) {
		String source = normalizeRelativePath(sourcePath);
		String destination = normalizeRelativePath(destinationPath);

		if (source.equals(destination)) {
			throw new IllegalArgumentException("파일 이동의 원본과 목적지는 같을 수 없습니다.");
		}

		FileCleanupTask task = FileCleanupTask.builder()
			.taskType(TaskType.MOVE)
			.sourcePath(source)
			.destinationPath(destination)
			.build();

		insertTask(task);
	}

	/**
	 * 기존 트랜잭션에 참여하여 파일 삭제 작업을 저장한다.
	 * 물리 파일 삭제는 수행하지 않는다.
	 */
	@Transactional(propagation = Propagation.MANDATORY)
	public void enqueueDelete(String sourcePath) {
		String source = normalizeRelativePath(sourcePath);

		FileCleanupTask task = FileCleanupTask.builder()
			.taskType(TaskType.DELETE)
			.sourcePath(source)
			.build();

		insertTask(task);
	}

	private void insertTask(FileCleanupTask task) {
		int affectedRows = taskMapper.insertTask(task);

		if (affectedRows != 1) {
			throw new IllegalStateException("파일 정리 작업 등록에 실패했습니다.");
		}
	}

	/**
	 * 저장소 기준 상대 경로인지 검사하고 정규화한다.
	 * 실제 저장소 경계와 심볼릭 링크 검사는 실행기에서 수행한다.
	 */
	private String normalizeRelativePath(String rawPath) {
		if (rawPath == null || rawPath.isBlank()) {
			throw new IllegalArgumentException("파일 정리 경로가 비어 있습니다.");
		}

		Path path = Path.of(rawPath).normalize();

		if (path.isAbsolute() || path.toString().isEmpty() || path.startsWith("..")) {

			throw new IllegalArgumentException("파일 정리 경로는 저장소 내부의 상대 경로여야 합니다.");
		}

		String normalizedPath = path.toString();

		if (normalizedPath.length() > MAX_PATH_LENGTH) {
			throw new IllegalArgumentException("파일 정리 경로가 너무 깁니다.");
		}

		return normalizedPath;
	}

	/**
	 * 업로드 실패·롤백 후 남은 파일의 삭제 작업을 독립적으로 저장한다.
	 * 원래 트랜잭션의 롤백과 관계없이 작업 기록을 남긴다.
	 */
	@Transactional(propagation = Propagation.REQUIRES_NEW)
	public void enqueueFailedUploadDelete(String sourcePath) {
		String source = normalizeRelativePath(sourcePath);

		FileCleanupTask task = FileCleanupTask.builder()
			.taskType(TaskType.DELETE)
			.sourcePath(source)
			.build();

		insertTask(task);
	}
}