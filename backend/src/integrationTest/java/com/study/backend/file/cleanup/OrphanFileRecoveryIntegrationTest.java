package com.study.backend.file.cleanup;

import static org.assertj.core.api.Assertions.*;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.attribute.FileTime;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.context.TestPropertySource;

import com.study.backend.common.IntegrationTestBase;
import com.study.backend.file.cleanup.model.OrphanFileCandidate;
import com.study.backend.file.cleanup.model.OrphanFileCandidate.Reason;
import com.study.backend.file.cleanup.service.OrphanFileRecoveryService;
import com.study.backend.file.cleanup.service.OrphanFileScanner;

@TestPropertySource(properties = {
	"file.cleanup.enabled=false",
	"file.orphan.recovery-enabled=false",
	"file.orphan.minimum-age-hours=24",
	"free-board.path=/free/",
	"free-board.deleted-path=/free/deleted/",
	"gallery-board.picture.path=/gallery/",
	"gallery-board.picture.deleted-path=/gallery/deleted/",
	"gallery-board.thumbnail.path=/thumbnail/"
})
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_CLASS)
class OrphanFileRecoveryIntegrationTest extends IntegrationTestBase {

	@TempDir
	static Path storageRoot;

	@DynamicPropertySource
	static void registerStoragePath(DynamicPropertyRegistry registry) {
		registry.add("store.base-path", () -> storageRoot.toString());
	}

	@Autowired
	OrphanFileScanner scanner;

	@Autowired
	OrphanFileRecoveryService recoveryService;

	@Autowired
	JdbcTemplate jdbcTemplate;

	private String storeName;
	private String sourcePath;
	private String destinationPath;
	private Path sourceFile;
	private Long boardId;

	@BeforeEach
	void setUp() throws IOException {
		Files.createDirectories(storageRoot.resolve("free/deleted"));
		Files.createDirectories(storageRoot.resolve("gallery/deleted"));
		Files.createDirectories(storageRoot.resolve("thumbnail"));

		storeName = UUID.randomUUID().toString();

		sourcePath = Path.of(
			"gallery",
			storeName + ".jpeg"
		).toString();

		destinationPath = Path.of(
			"gallery",
			"deleted",
			storeName + ".jpeg"
		).toString();

		sourceFile = storageRoot.resolve(sourcePath);

		Files.writeString(sourceFile, "orphan-test-data");

		// 24시간 기준보다 충분히 오래된 파일로 만든다.
		Files.setLastModifiedTime(
			sourceFile,
			FileTime.from(
				Instant.now().minus(Duration.ofHours(48))
			)
		);
	}

	@AfterEach
	void cleanUp() throws IOException {
		// 이번 테스트가 만든 데이터만 제거한다.
		jdbcTemplate.update(
			"""
			DELETE FROM file_cleanup_task
			WHERE source_path IN (?, ?)
			""",
			sourcePath,
			destinationPath
		);

		jdbcTemplate.update(
			"DELETE FROM file WHERE store_name = ?",
			storeName
		);

		if (boardId != null) {
			jdbcTemplate.update(
				"DELETE FROM board WHERE id = ?",
				boardId
			);
		}

		Files.deleteIfExists(sourceFile);
		Files.deleteIfExists(storageRoot.resolve(destinationPath));
	}

	@Test
	@DisplayName("오래된 미참조 파일을 재확인한 뒤 MOVE 작업을 등록한다")
	void recover_unreferencedFile_registersMove() throws Exception {
		OrphanFileCandidate candidate = findCandidate();

		assertThat(candidate.reason())
			.isEqualTo(Reason.UNREFERENCED);

		boolean registered = recoveryService.enqueueMoveIfStillCandidate(candidate);

		assertThat(registered).isTrue();
		assertThat(countSourceTasks()).isEqualTo(1L);

		String taskType = jdbcTemplate.queryForObject(
			"""
			SELECT task_type
			FROM file_cleanup_task
			WHERE source_path = ?
			""",
			String.class,
			sourcePath
		);

		String savedDestination = jdbcTemplate.queryForObject(
			"""
			SELECT destination_path
			FROM file_cleanup_task
			WHERE source_path = ?
			""",
			String.class,
			sourcePath
		);

		assertThat(taskType).isEqualTo("MOVE");
		assertThat(savedDestination).isEqualTo(destinationPath);

		// 복구 서비스는 작업만 등록한다. 실제 이동은 아직 하지 않는다.
		assertThat(sourceFile).hasContent("orphan-test-data");
		assertThat(storageRoot.resolve(destinationPath)).doesNotExist();
	}

	@Test
	@DisplayName("최초 탐지 후 활성 참조가 생기면 MOVE 작업을 등록하지 않는다")
	void recover_referenceAppears_doesNotRegisterMove() throws Exception {
		// 처음에는 DB 참조가 없어서 후보로 탐지된다.
		OrphanFileCandidate candidate = findCandidate();

		// 탐지 이후 메타데이터가 커밋된 상황을 만든다.
		insertActiveFileReference();

		boolean registered = recoveryService.enqueueMoveIfStillCandidate(candidate);

		assertThat(registered).isFalse();
		assertThat(countSourceTasks()).isZero();

		assertThat(sourceFile).hasContent("orphan-test-data");
		assertThat(storageRoot.resolve(destinationPath)).doesNotExist();
	}

	@Test
	@DisplayName("같은 후보를 반복 처리해도 MOVE 작업은 한 번만 등록한다")
	void recover_sameCandidateTwice_doesNotDuplicateTask()
		throws Exception {

		OrphanFileCandidate candidate = findCandidate();

		boolean firstRegistered = recoveryService.enqueueMoveIfStillCandidate(candidate);

		boolean secondRegistered = recoveryService.enqueueMoveIfStillCandidate(candidate);

		assertThat(firstRegistered).isTrue();
		assertThat(secondRegistered).isFalse();
		assertThat(countSourceTasks()).isEqualTo(1L);

		assertThat(sourceFile).exists();
	}

	private OrphanFileCandidate findCandidate() throws IOException {
		List<OrphanFileCandidate> candidates = scanner.scan();

		List<OrphanFileCandidate> matchingCandidates = candidates.stream()
			.filter(candidate -> sourcePath.equals(candidate.relativePath()))
			.toList();

		assertThat(matchingCandidates).hasSize(1);

		return matchingCandidates.get(0);
	}

	private long countSourceTasks() {
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

	private void insertActiveFileReference() throws IOException {
		String title = "orphan-test-" + storeName;

		jdbcTemplate.update(
			"""
			INSERT INTO board (
				title,
				content,
				board_type_id,
				state,
				version
			)
			VALUES (?, '고아 복구 테스트', 3, false, 0)
			""",
			title
		);

		boardId = jdbcTemplate.queryForObject(
			"SELECT id FROM board WHERE title = ?",
			Long.class,
			title
		);

		jdbcTemplate.update(
			"""
			INSERT INTO file (
				file_name,
				store_name,
				extension,
				path,
				file_size,
				board_id,
				state
			)
			VALUES (?, ?, '.jpeg', '/gallery/', ?, ?, false)
			""",
			storeName + ".jpeg",
			storeName,
			Files.size(sourceFile),
			boardId
		);
	}
}