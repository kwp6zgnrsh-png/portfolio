package com.study.backend.file.cleanup.scheduler;

import java.io.IOException;
import java.util.List;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import com.study.backend.file.cleanup.mapper.FileCleanupTaskMapper;
import com.study.backend.file.cleanup.model.FileCleanupTask;
import com.study.backend.file.cleanup.service.FileCleanupCompletionService;
import com.study.backend.file.cleanup.service.FileCleanupWorker;
import com.study.backend.file.cleanup.service.OrphanFileRecoveryBatch;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@Component
@RequiredArgsConstructor
@ConditionalOnProperty(
	prefix = "file.cleanup",
	name = "enabled",
	havingValue = "true"
)
public class FileCleanupScheduler {

	private static final int BATCH_SIZE = 50;
	private static final int MAX_ERROR_LENGTH = 2000;

	private final FileCleanupCompletionService completionService;
	private final FileCleanupTaskMapper taskMapper;
	private final FileCleanupWorker worker;
	private final OrphanFileRecoveryBatch recoveryBatch;

	@Value("${file.orphan.recovery-enabled:false}")
	private boolean orphanRecoveryEnabled;

	/**
	 * 고아 복구 후 기존 파일 작업을 실행한다.
	 * 탐지 중 파일 접근 오류는 기존 작업 실행을 막지 않는다.
	 * DB 오류 및 예상하지 못한 오류는 이번 배치를 중단한다.
	 */
	@Scheduled(fixedDelayString = "${file.cleanup.fixed-delay-ms:60000}",
			   initialDelayString = "${file.cleanup.initial-delay-ms:60000}")
	public void processDueTasks() {
		List<FileCleanupTask> tasks;

		try {
			if (orphanRecoveryEnabled) {
				try {
					recoveryBatch.recoverCandidates();
				} catch (IOException e) {
					log.error(
						"고아 파일 탐지·재확인 중 파일 접근 실패: " + "고아 복구는 중단하고 기존 정리 작업은 계속합니다.", e
					);
				}
			}

			tasks = taskMapper.findDueTasks(BATCH_SIZE);
		} catch (Exception e) {
			log.error("파일 정리 배치 준비 실패: 이번 실행을 중단합니다.", e);
			return;
		}

		for (FileCleanupTask task : tasks) {
			processTask(task);
		}
	}

	private void processTask(FileCleanupTask task) {
		try {
			worker.execute(task);
			completionService.complete(task);
		} catch (Exception e) {
			scheduleRetry(task, e);
		}
	}

	private void scheduleRetry(FileCleanupTask task, Exception cause) {
		long delaySeconds = calculateRetryDelaySeconds(
			task.getAttempts()
		);

		log.error(
			"파일 정리 실패: taskId={}, nextRetrySeconds={}",
			task.getId(),
			delaySeconds,
			cause
		);

		try {
			int affectedRows = taskMapper.recordFailure(
				task.getId(),
				delaySeconds,
				summarizeError(cause)
			);

			if (affectedRows != 1) {
				log.warn(
					"재시도할 파일 정리 작업 행을 찾지 못했습니다: taskId={}",
					task.getId()
				);
			}
		} catch (Exception updateException) {
			// 갱신에 실패해도 기존 작업 행은 남아 있으므로
			// 다음 스케줄 실행에서 다시 조회된다.
			log.error(
				"파일 정리 재시도 정보 저장 실패: taskId={}",
				task.getId(),
				updateException
			);
		}
	}

	/** 실패 시 1분부터 시작하여 최대 1시간까지 대기한다. */
	private long calculateRetryDelaySeconds(int attempts) {
		int exponent = Math.min(Math.max(attempts, 0), 6);

		return Math.min(
			60L * (1L << exponent),
			3600L
		);
	}

	private String summarizeError(Exception exception) {
		String message = exception.getClass().getSimpleName() + ": " + exception.getMessage();

		if (message.length() > MAX_ERROR_LENGTH) {
			return message.substring(0, MAX_ERROR_LENGTH);
		}

		return message;
	}
}