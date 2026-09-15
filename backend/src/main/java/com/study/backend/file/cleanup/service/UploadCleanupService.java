package com.study.backend.file.cleanup.service;

import java.nio.file.Path;
import java.util.List;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import com.study.backend.file.cleanup.model.FileCleanupTask;
import com.study.backend.file.cleanup.model.FileCleanupTask.TaskType;

import lombok.extern.slf4j.Slf4j;

@Slf4j
@Service
public class UploadCleanupService {

	private final FileCleanupWorker worker;
	private final FileCleanupTaskService taskService;
	private final Path storageRoot;

	public UploadCleanupService(FileCleanupWorker worker, FileCleanupTaskService taskService, @Value("${store.base-path}") String storeBasePath) {
		if (storeBasePath == null || storeBasePath.isBlank()) {
			throw new IllegalArgumentException("파일 저장소 경로가 비어 있습니다.");
		}

		this.worker = worker;
		this.taskService = taskService;
		this.storageRoot = Path.of(storeBasePath)
			.toAbsolutePath()
			.normalize();
	}

	/**
	 * 롤백 시 새로 생성한 파일들을 정리하도록 등록한다.
	 * 반환값이 false이면 호출자가 실패 시 직접 정리해야 한다.
	 * paths는 이후 파일 경로가 추가되는 동일한 리스트를 참조한다.
	 */
	public boolean registerRollbackCleanup(List<Path> paths) {
		if (!TransactionSynchronizationManager.isActualTransactionActive()
			|| !TransactionSynchronizationManager.isSynchronizationActive()) {
			return false;
		}

		TransactionSynchronizationManager.registerSynchronization(
			new TransactionSynchronization() {
				@Override
				public void afterCompletion(int status) {
					if (status == STATUS_ROLLED_BACK) {
						cleanupFiles(paths);
					} else if (status == STATUS_UNKNOWN) {
						// 커밋 여부를 모르면 정상 파일일 수 있으므로 삭제하지 않는다.
						log.error("트랜잭션 결과 불명으로 업로드 파일 정리를 보류합니다: {}", paths
						);
					}
				}
			}
		);

		return true;
	}

	/** 신규 파일을 정리하며, 정리 실패가 원래 예외를 덮어쓰지 않게 한다. */
	public void cleanupFiles(List<Path> paths) {
		if (paths == null || paths.isEmpty()) {
			return;
		}

		for (Path path : paths) {
			if (path != null) {
				cleanupFile(path);
			}
		}
	}

	private void cleanupFile(Path path) {
		String relativePath;

		try {
			relativePath = toRelativePath(path);
		} catch (RuntimeException e) {
			log.error("업로드 정리 경로 검증 실패: {}", path, e);
			return;
		}

		FileCleanupTask task = FileCleanupTask.builder()
			.taskType(TaskType.DELETE)
			.sourcePath(relativePath)
			.build();

		try {
			worker.execute(task);
		} catch (Exception cleanupException) {
			log.error(
				"업로드 파일 즉시 정리 실패: {}",
				path,
				cleanupException
			);

			try {
				taskService.enqueueFailedUploadDelete(relativePath);
			} catch (Exception registrationException) {
				// 원래 업로드 예외를 덮어쓰지 않는다.
				// 작업 기록도 남지 못한 파일은 6단계 탐지로 복구한다.
				log.error(
					"업로드 파일 정리 작업 등록도 실패했습니다: {}",
					path,
					registrationException
				);
			}
		}
	}

	private String toRelativePath(Path path) {
		Path target = path.toAbsolutePath().normalize();

		if (target.equals(storageRoot)
			|| !target.startsWith(storageRoot)) {
			throw new IllegalArgumentException("업로드 정리 대상이 저장소 밖에 있습니다.");
		}
		return storageRoot.relativize(target).toString();
	}
}