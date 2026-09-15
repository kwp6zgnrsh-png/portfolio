package com.study.backend.file.cleanup;

import static org.assertj.core.api.Assertions.*;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.UUID;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.TestPropertySource;

import com.study.backend.common.IntegrationTestBase;
import com.study.backend.file.cleanup.model.FileCleanupTask;
import com.study.backend.file.cleanup.model.FileCleanupTask.TaskType;
import com.study.backend.file.cleanup.service.FileCleanupCompletionService;
import com.study.backend.file.cleanup.service.FileCleanupWorker;

@TestPropertySource(properties = {
	"file.cleanup.enabled=false",
	"file.orphan.retention-days=7"
})
class FileCleanupCompletionIntegrationTest extends IntegrationTestBase {

	@TempDir
	Path storageRoot;

	@Autowired
	FileCleanupCompletionService completionService;

	@Autowired
	JdbcTemplate jdbcTemplate;

	private String sourcePath;
	private String destinationPath;

	@AfterEach
	void cleanupDatabase() {
		if (sourcePath == null || destinationPath == null) {
			return;
		}

		// 이번 테스트에서 생성한 작업만 제거한다.
		jdbcTemplate.update(
			"""
			DELETE FROM file_cleanup_task
			WHERE source_path IN (?, ?)
			""",
			sourcePath,
			destinationPath
		);
	}

	@Test
	@DisplayName("MOVE 성공 시 파일을 이동하고 7일 뒤 DELETE 작업을 등록한다")
	void completeMove_movesFileAndSchedulesDelete() throws Exception {
		String fileName = UUID.randomUUID() + ".jpeg";

		sourcePath = Path.of("gallery", fileName).toString();
		destinationPath = Path.of(
			"gallery", "deleted", fileName
		).toString();

		Path source = storageRoot.resolve(sourcePath);
		Path destination = storageRoot.resolve(destinationPath);

		Files.createDirectories(source.getParent());
		Files.createDirectories(destination.getParent());
		Files.writeString(source, "test-image");

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

		Long moveTaskId = jdbcTemplate.queryForObject(
			"""
			SELECT id
			FROM file_cleanup_task
			WHERE task_type = 'MOVE'
			  AND source_path = ?
			""",
			Long.class,
			sourcePath
		);

		FileCleanupTask moveTask = FileCleanupTask.builder()
			.id(moveTaskId)
			.taskType(TaskType.MOVE)
			.sourcePath(sourcePath)
			.destinationPath(destinationPath)
			.build();

		FileCleanupWorker worker =
			new FileCleanupWorker(storageRoot.toString());

		// 스케줄러와 동일한 실행 순서
		worker.execute(moveTask);
		completionService.complete(moveTask);

		// 1. 원본이 없어지고 목적지에 같은 내용이 존재한다.
		assertThat(source).doesNotExist();
		assertThat(destination).hasContent("test-image");

		// 2. 기존 MOVE 작업이 제거됐다.
		Long remainingMoveTasks = jdbcTemplate.queryForObject(
			"SELECT COUNT(*) FROM file_cleanup_task WHERE id = ?",
			Long.class,
			moveTaskId
		);

		assertThat(remainingMoveTasks).isZero();

		// 3. 이동 목적지를 삭제하는 작업이 하나 등록됐다.
		List<String> taskTypes = jdbcTemplate.queryForList(
			"""
			SELECT task_type
			FROM file_cleanup_task
			WHERE source_path = ?
			""",
			String.class,
			destinationPath
		);

		assertThat(taskTypes).containsExactly("DELETE");

		String deleteDestination = jdbcTemplate.queryForObject(
			"""
			SELECT destination_path
			FROM file_cleanup_task
			WHERE source_path = ?
			""",
			String.class,
			destinationPath
		);

		assertThat(deleteDestination).isNull();

		// 4. 작업 생성 시각으로부터 7일 뒤로 예약됐다.
		// DB 내부에서 비교하여 JVM과 DB의 시간대 차이를 피한다.
		Long delaySeconds = jdbcTemplate.queryForObject(
			"""
			SELECT TIMESTAMPDIFF(
				SECOND,
				created_at,
				next_attempt_at
			)
			FROM file_cleanup_task
			WHERE source_path = ?
			""",
			Long.class,
			destinationPath
		);

		assertThat(delaySeconds).isEqualTo(7L * 24 * 60 * 60);

		// 5. 아직 실행 시각이 되지 않았다.
		Long dueTasks = jdbcTemplate.queryForObject(
			"""
			SELECT COUNT(*)
			FROM file_cleanup_task
			WHERE source_path = ?
			  AND next_attempt_at <= CURRENT_TIMESTAMP
			""",
			Long.class,
			destinationPath
		);

		assertThat(dueTasks).isZero();

		// 6. 완료 처리를 다시 호출해도 중복 예약되지 않는다.
		completionService.complete(moveTask);

		Long deleteTaskCount = jdbcTemplate.queryForObject(
			"""
			SELECT COUNT(*)
			FROM file_cleanup_task
			WHERE source_path = ?
			  AND task_type = 'DELETE'
			""",
			Long.class,
			destinationPath
		);

		assertThat(deleteTaskCount).isEqualTo(1L);
	}
}