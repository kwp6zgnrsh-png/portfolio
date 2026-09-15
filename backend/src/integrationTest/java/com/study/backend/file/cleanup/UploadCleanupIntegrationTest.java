package com.study.backend.file.cleanup;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.TestPropertySource;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

import com.study.backend.common.IntegrationTestBase;
import com.study.backend.file.cleanup.model.FileCleanupTask;
import com.study.backend.file.cleanup.service.FileCleanupTaskService;
import com.study.backend.file.cleanup.service.FileCleanupWorker;
import com.study.backend.file.cleanup.service.UploadCleanupService;

@TestPropertySource(properties = "file.cleanup.enabled=false")
class UploadCleanupIntegrationTest extends IntegrationTestBase {

	@TempDir
	Path storageRoot;

	@Autowired
	FileCleanupTaskService taskService;

	@Autowired
	JdbcTemplate jdbcTemplate;

	@Autowired
	PlatformTransactionManager transactionManager;

	private TransactionTemplate transactionTemplate;
	private FileCleanupWorker worker;
	private UploadCleanupService cleanupService;

	private Path uploadedFile;
	private String relativePath;
	private String rollbackMarkerPath;

	@BeforeEach
	void setUp() throws IOException {
		transactionTemplate =
			new TransactionTemplate(transactionManager);

		worker = spy(new FileCleanupWorker(storageRoot.toString()));

		cleanupService = new UploadCleanupService(
			worker,
			taskService,
			storageRoot.toString()
		);

		String testId = UUID.randomUUID().toString();

		relativePath = Path.of(
			"gallery",
			testId + ".jpeg"
		).toString();

		rollbackMarkerPath = Path.of(
			"gallery",
			testId + "-rollback-marker.jpeg"
		).toString();

		uploadedFile = storageRoot.resolve(relativePath);
		Files.createDirectories(uploadedFile.getParent());
	}

	@AfterEach
	void cleanUpDatabase() {
		// REQUIRES_NEW로 커밋된 테스트 행도 직접 정리한다.
		// 다른 테스트나 기존 작업은 삭제하지 않는다.
		jdbcTemplate.update(
			"""
			DELETE FROM file_cleanup_task
			WHERE source_path IN (?, ?)
			""",
			relativePath,
			rollbackMarkerPath
		);
	}

	@Test
	@DisplayName("커밋하면 생성한 파일을 유지하고 정리 작업을 등록하지 않는다")
	void commit_keepsFile() throws IOException {
		Files.writeString(uploadedFile, "test image");

		transactionTemplate.executeWithoutResult(status -> {
			boolean registered =
				cleanupService.registerRollbackCleanup(
					List.of(uploadedFile)
				);

			assertThat(registered).isTrue();
		});

		// executeWithoutResult가 반환되면 커밋과 콜백 처리가 끝난 상태다.
		assertThat(uploadedFile).exists();
		assertThat(countTasks(relativePath)).isZero();
		verifyNoInteractions(worker);
	}

	@Test
	@DisplayName("롤백하면 콜백 등록 이후 추적 목록에 추가된 파일도 삭제한다")
	void rollback_deletesTrackedFile() throws IOException {
		Files.writeString(uploadedFile, "test image");

		List<Path> trackedPaths = new ArrayList<>();

		transactionTemplate.executeWithoutResult(status -> {
			boolean registered =
				cleanupService.registerRollbackCleanup(trackedPaths);

			assertThat(registered).isTrue();

			// 실제 createFiles처럼 콜백 등록 후 경로를 추가한다.
			trackedPaths.add(uploadedFile);

			status.setRollbackOnly();
		});

		assertThat(uploadedFile).doesNotExist();
		assertThat(countTasks(relativePath)).isZero();
	}

	@Test
	@DisplayName("롤백 후 파일 삭제가 실패하면 재시도 작업은 별도 커밋으로 남는다")
	void rollback_deleteFails_retryTaskSurvives() throws Exception {
		Files.writeString(uploadedFile, "test image");

		doThrow(new IOException("테스트용 파일 삭제 실패"))
			.when(worker)
			.execute(any(FileCleanupTask.class));

		transactionTemplate.executeWithoutResult(status -> {
			// 바깥 트랜잭션에 참여하는 작업이다.
			// 이 행이 사라지는 것으로 실제 롤백도 확인한다.
			taskService.enqueueDelete(rollbackMarkerPath);

			boolean registered =
				cleanupService.registerRollbackCleanup(
					List.of(uploadedFile)
				);

			assertThat(registered).isTrue();

			status.setRollbackOnly();
		});

		// 물리 삭제는 실패했으므로 파일이 남는다.
		assertThat(uploadedFile).exists();

		// 바깥 트랜잭션에서 저장한 행은 롤백된다.
		assertThat(countTasks(rollbackMarkerPath)).isZero();

		// 롤백 콜백에서 REQUIRES_NEW로 저장한 행은 남는다.
		assertThat(countTasks(relativePath)).isEqualTo(1L);

		String taskType = jdbcTemplate.queryForObject(
			"""
			SELECT task_type
			FROM file_cleanup_task
			WHERE source_path = ?
			""",
			String.class,
			relativePath
		);

		assertThat(taskType).isEqualTo("DELETE");

		Integer attempts = jdbcTemplate.queryForObject(
			"""
			SELECT attempts
			FROM file_cleanup_task
			WHERE source_path = ?
			""",
			Integer.class,
			relativePath
		);

		assertThat(attempts).isZero();
	}

	private long countTasks(String sourcePath) {
		Long count = jdbcTemplate.queryForObject(
			"""
			SELECT COUNT(*)
			FROM file_cleanup_task
			WHERE source_path = ?
			""",
			Long.class,
			sourcePath
		);

		return count == null ? 0L : count;
	}
}