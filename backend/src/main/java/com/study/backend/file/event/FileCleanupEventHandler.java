package com.study.backend.file.event;

import java.nio.file.Path;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

import com.study.backend.file.cleanup.service.FileCleanupTaskService;

@Component
public class FileCleanupEventHandler {

	private final FileCleanupTaskService cleanupTaskService;
	private final Path storageRoot;

	public FileCleanupEventHandler(FileCleanupTaskService cleanupTaskService,
								   @Value("${store.base-path}") String storeBasePath) {
		if (storeBasePath == null || storeBasePath.isBlank()) {
			throw new IllegalArgumentException("파일 저장소 경로가 비어 있습니다.");
		}

		this.cleanupTaskService = cleanupTaskService;
		this.storageRoot = Path.of(storeBasePath)
			.toAbsolutePath()
			.normalize();
	}

	/**
	 * 커밋 전에 파일 정리 작업을 같은 트랜잭션에 저장한다.
	 * 등록 실패 시 예외를 전파하여 기존 변경도 함께 롤백한다.
	 */
	@TransactionalEventListener(phase = TransactionPhase.BEFORE_COMMIT)
	public void handleFileCleanup(FileCleanupEvent event) {
		for (FileCleanupEvent.FileMoveTask task : event.moveTasks()) {
			cleanupTaskService.enqueueMove(
				toRelativePath(task.srcPath()),
				toRelativePath(task.destPath())
			);
		}

		for (String path : event.deletePaths()) {
			cleanupTaskService.enqueueDelete(
				toRelativePath(path)
			);
		}
	}

	/**
	 * 이벤트의 파일 경로를 저장소 기준 상대 경로로 변환한다.
	 * 저장소 자체와 저장소 밖의 경로는 거절한다.
	 */
	private String toRelativePath(String rawPath) {
		if (rawPath == null || rawPath.isBlank()) {
			throw new IllegalArgumentException("파일 정리 경로가 비어 있습니다.");
		}

		Path target = Path.of(rawPath)
			.toAbsolutePath()
			.normalize();

		if (target.equals(storageRoot) || !target.startsWith(storageRoot)) {

			throw new IllegalArgumentException("파일 정리 대상이 저장소 내부 경로가 아닙니다.");
		}

		return storageRoot.relativize(target).toString();
	}
}