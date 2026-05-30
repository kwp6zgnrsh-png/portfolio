package com.study.backend.file.util;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import lombok.extern.slf4j.Slf4j;

@Slf4j
public class FileCleanupHelper {

	private FileCleanupHelper() {}

	/** 파일 목록을 삭제한다. 실패 시 로깅만 하고 예외를 전파하지 않는다. */
	public static void cleanupFiles(List<Path> paths) {
		if (paths == null || paths.isEmpty()) return;
		for (Path path : paths) {
			if (path == null) continue;
			try {
				Files.deleteIfExists(path);
			} catch (IOException e) {
				log.error("파일 정리 실패: {}", path, e);
			}
		}
	}

	/** target 리스트에 파일 경로들을 추가한다. */
	public static void addFiles(List<Path> target, List<Path> files) {
		if (files != null && !files.isEmpty()) {
			target.addAll(files);
		}
	}

	/** 트랜잭션 롤백 시 생성된 파일들을 자동 삭제하도록 콜백을 등록한다. */
	public static void registerRollbackCleanup(List<Path> createdFiles) {
		if (!TransactionSynchronizationManager.isSynchronizationActive()) return;
		TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
			@Override
			public void afterCompletion(int status) {
				if (status == TransactionSynchronization.STATUS_ROLLED_BACK) {
					cleanupFiles(createdFiles);
				}
			}
		});
	}
}
