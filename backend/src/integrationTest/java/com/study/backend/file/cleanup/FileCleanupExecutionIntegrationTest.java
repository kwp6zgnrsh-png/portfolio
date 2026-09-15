package com.study.backend.file.cleanup;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.UUID;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.dao.DataAccessResourceFailureException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.TestPropertySource;

import com.study.backend.common.IntegrationTestBase;
import com.study.backend.file.cleanup.mapper.FileCleanupTaskMapper;
import com.study.backend.file.cleanup.model.FileCleanupTask;
import com.study.backend.file.cleanup.scheduler.FileCleanupScheduler;
import com.study.backend.file.cleanup.service.FileCleanupCompletionService;
import com.study.backend.file.cleanup.service.FileCleanupWorker;
import com.study.backend.file.cleanup.service.OrphanFileRecoveryBatch;

@TestPropertySource(properties = {
	"file.cleanup.enabled=false",
	"file.orphan.recovery-enabled=false",
	"file.orphan.retention-days=7"
})
class FileCleanupExecutionIntegrationTest extends IntegrationTestBase {

	@TempDir
	Path storageRoot;

	@Autowired
	FileCleanupTaskMapper taskMapper;

	@Autowired
	FileCleanupCompletionService completionService;

	@Autowired
	JdbcTemplate jdbcTemplate;

	private FileCleanupWorker worker;
	private FileCleanupScheduler scheduler;

	private String sourcePath;
	private String destinationPath;

	@BeforeEach
	void setUp() throws Exception {
		String fileName = UUID.randomUUID() + ".jpeg";

		sourcePath = Path.of("gallery", fileName).toString();
		destinationPath = Path.of(
			"gallery", "deleted", fileName
		).toString();

		Files.createDirectories(storageRoot.resolve("gallery/deleted"));

		worker = spy(new FileCleanupWorker(storageRoot.toString()));
		scheduler = createScheduler(completionService);
	}

	@AfterEach
	void cleanupDatabase() {
		jdbcTemplate.update(
			"""
			DELETE FROM file_cleanup_task
			WHERE source_path IN (?, ?)
			""",
			sourcePath,
			destinationPath
		);
	}

	// ── 예약 삭제 ──────────────────────────────────────

	@Test
	@DisplayName("예약 시각 전에는 보존하고 시각이 지나면 파일과 작업을 삭제한다")
	void scheduledDelete_runsOnlyWhenDue() throws Exception {
		Path file = storageRoot.resolve(destinationPath);
		Files.writeString(file, "retained-file");

		taskMapper.insertDelayedDelete(destinationPath, 7);

		// 아직 7일이 지나지 않았으므로 실행하지 않는다.
		scheduler.processDueTasks();

		assertThat(file).hasContent("retained-file");
		assertThat(countTasks(destinationPath)).isEqualTo(1L);
		verifyNoInteractions(worker);

		// 실제로 기다리는 대신 테스트 작업의 실행 시각만 과거로 바꾼다.
		makeDue(destinationPath);

		scheduler.processDueTasks();

		assertThat(file).doesNotExist();
		assertThat(countTasks(destinationPath)).isZero();
	}

	// ── 삭제 실패·재시도 ─────────────────────────────────

	@Test
	@DisplayName("삭제 실패 시 재시도 정보를 저장하고 다음 실행에서 삭제한다")
	void deleteFailure_retriesSuccessfully() throws Exception {
		Path file = storageRoot.resolve(destinationPath);
		Files.writeString(file, "delete-retry");

		taskMapper.insertDelayedDelete(destinationPath, 7);
		makeDue(destinationPath);

		// 첫 실행만 실패하고, 다음에는 실제 파일 삭제를 수행한다.
		doThrow(new IOException("테스트용 삭제 실패"))
			.doCallRealMethod()
			.when(worker)
			.execute(any(FileCleanupTask.class));

		scheduler.processDueTasks();

		assertThat(file).exists();
		assertRetryScheduled(destinationPath, "테스트용 삭제 실패");

		makeDue(destinationPath);
		scheduler.processDueTasks();

		assertThat(file).doesNotExist();
		assertThat(countTasks(destinationPath)).isZero();
	}

	// ── 이동 실패·재시도 ─────────────────────────────────

	@Test
	@DisplayName("이동 실패 시 원본을 유지하고 재시도 성공 후 DELETE를 예약한다")
	void moveFailure_retriesAndSchedulesDelete() throws Exception {
		Path source = storageRoot.resolve(sourcePath);
		Path destination = storageRoot.resolve(destinationPath);

		Files.writeString(source, "move-retry");
		insertMove();

		doThrow(new IOException("테스트용 이동 실패"))
			.doCallRealMethod()
			.when(worker)
			.execute(any(FileCleanupTask.class));

		scheduler.processDueTasks();

		assertThat(source).hasContent("move-retry");
		assertThat(destination).doesNotExist();
		assertRetryScheduled(sourcePath, "테스트용 이동 실패");

		makeDue(sourcePath);
		scheduler.processDueTasks();

		assertThat(source).doesNotExist();
		assertThat(destination).hasContent("move-retry");

		assertThat(countTasks(sourcePath)).isZero();
		assertThat(countTasks(destinationPath)).isEqualTo(1L);

		String taskType = jdbcTemplate.queryForObject(
			"""
			SELECT task_type
			FROM file_cleanup_task
			WHERE source_path = ?
			""",
			String.class,
			destinationPath
		);

		Long retentionSeconds = jdbcTemplate.queryForObject(
			"""
			SELECT TIMESTAMPDIFF(
				SECOND, created_at, next_attempt_at
			)
			FROM file_cleanup_task
			WHERE source_path = ?
			""",
			Long.class,
			destinationPath
		);

		assertThat(taskType).isEqualTo("DELETE");
		assertThat(retentionSeconds).isEqualTo(7L * 24 * 60 * 60);
	}

	// ── 물리 작업 성공 후 완료 처리 실패 ─────────────────

	@Test
	@DisplayName("파일 삭제 후 완료 처리가 실패해도 재실행하여 작업을 정리한다")
	void completionFailure_afterDelete_recoversOnRetry() throws Exception {
		Path file = storageRoot.resolve(destinationPath);
		Files.writeString(file, "completion-retry");

		taskMapper.insertDelayedDelete(destinationPath, 7);
		makeDue(destinationPath);

		FileCleanupCompletionService failingCompletion =
			mock(FileCleanupCompletionService.class);

		// 첫 완료 호출은 실패한다.
		// 두 번째는 실제 Spring 서비스에 위임하여 커밋한다.
		doThrow(
			new DataAccessResourceFailureException(
				"테스트용 완료 처리 실패"
			)
		)
			.doAnswer(invocation -> {
				FileCleanupTask task = invocation.getArgument(0);
				completionService.complete(task);
				return null;
			})
			.when(failingCompletion)
			.complete(any(FileCleanupTask.class));

		scheduler = createScheduler(failingCompletion);

		scheduler.processDueTasks();

		// 물리 삭제는 이미 성공했지만 DB 작업 행은 남는다.
		assertThat(file).doesNotExist();
		assertRetryScheduled(
			destinationPath,
			"테스트용 완료 처리 실패"
		);

		makeDue(destinationPath);
		scheduler.processDueTasks();

		// 파일이 이미 없어도 성공 처리하고 남은 작업을 제거한다.
		assertThat(file).doesNotExist();
		assertThat(countTasks(destinationPath)).isZero();
	}

	// ── 테스트 도우미 ───────────────────────────────────

	private FileCleanupScheduler createScheduler(
		FileCleanupCompletionService completion
	) {
		FileCleanupTaskMapper scopedMapper =
			mock(FileCleanupTaskMapper.class);

		/*
		 * 실제 Mapper로 실행 가능한 작업을 조회하되,
		 * 이번 테스트의 파일만 실행기에 전달한다.
		 */
		when(scopedMapper.findDueTasks(anyInt()))
			.thenAnswer(invocation -> {
				int limit = invocation.getArgument(0);

				List<FileCleanupTask> tasks =
					taskMapper.findDueTasks(limit);

				return tasks.stream()
					.filter(task ->
						sourcePath.equals(task.getSourcePath())
							|| destinationPath.equals(task.getSourcePath())
					)
					.toList();
			});

		when(scopedMapper.recordFailure(
			anyLong(),
			anyLong(),
			anyString()
		)).thenAnswer(invocation -> {
			Long taskId = invocation.getArgument(0);
			long retryDelaySeconds = invocation.getArgument(1);
			String lastError = invocation.getArgument(2);

			return taskMapper.recordFailure(
				taskId,
				retryDelaySeconds,
				lastError
			);
		});

		return new FileCleanupScheduler(
			completion,
			scopedMapper,
			worker,
			mock(OrphanFileRecoveryBatch.class)
		);
	}

	private void insertMove() {
		jdbcTemplate.update(
			"""
			INSERT INTO file_cleanup_task (
				task_type,
				source_path,
				destination_path
			)
			VALUES ('MOVE', ?, ?)
			""",
			sourcePath,
			destinationPath
		);
	}

	private void makeDue(String path) {
		jdbcTemplate.update(
			"""
			UPDATE file_cleanup_task
			SET next_attempt_at =
				DATE_SUB(CURRENT_TIMESTAMP, INTERVAL 1 SECOND)
			WHERE source_path = ?
			""",
			path
		);
	}

	private long countTasks(String path) {
		Long count = jdbcTemplate.queryForObject(
			"""
			SELECT COUNT(*)
			FROM file_cleanup_task
			WHERE source_path = ?
			""",
			Long.class,
			path
		);

		return count == null ? 0L : count;
	}

	private void assertRetryScheduled(
		String path,
		String expectedError
	) {
		Integer attempts = jdbcTemplate.queryForObject(
			"""
			SELECT attempts
			FROM file_cleanup_task
			WHERE source_path = ?
			""",
			Integer.class,
			path
		);

		String lastError = jdbcTemplate.queryForObject(
			"""
			SELECT last_error
			FROM file_cleanup_task
			WHERE source_path = ?
			""",
			String.class,
			path
		);

		Integer scheduledInFuture = jdbcTemplate.queryForObject(
			"""
			SELECT next_attempt_at > CURRENT_TIMESTAMP
			FROM file_cleanup_task
			WHERE source_path = ?
			""",
			Integer.class,
			path
		);

		assertThat(attempts).isEqualTo(1);
		assertThat(lastError).contains(expectedError);
		assertThat(scheduledInFuture).isEqualTo(1);
	}
}