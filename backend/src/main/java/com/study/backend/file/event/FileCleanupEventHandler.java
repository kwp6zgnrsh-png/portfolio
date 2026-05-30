package com.study.backend.file.event;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;

import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

import lombok.extern.slf4j.Slf4j;

@Slf4j
@Component
public class FileCleanupEventHandler {

	/**
	 * 트랜잭션 커밋 후 파일 이동/삭제를 수행한다.
	 * 실패 시 로깅만 하고 예외를 전파하지 않는다.
	 */
	@TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
	public void handleFileCleanup(FileCleanupEvent event) {
		for (FileCleanupEvent.FileMoveTask task : event.moveTasks()) {
			Path src = Paths.get(task.srcPath());
			if (!Files.exists(src)) {
				log.warn("물리 파일 없음, DB만 삭제 처리: {}", src);
				continue;
			}
			try {
				Files.move(src, Paths.get(task.destPath()), StandardCopyOption.REPLACE_EXISTING);
			} catch (IOException e) {
				log.error("파일 이동 실패: {}", task.srcPath(), e);
			}
		}

		for (String path : event.deletePaths()) {
			try {
				Files.deleteIfExists(Paths.get(path));
			} catch (IOException e) {
				log.error("파일 삭제 실패: {}", path, e);
			}
		}
	}
}
