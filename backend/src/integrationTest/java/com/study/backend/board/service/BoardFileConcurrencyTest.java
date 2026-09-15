package com.study.backend.board.service;

import static java.util.concurrent.TimeUnit.*;
import static org.assertj.core.api.Assertions.*;
import static org.awaitility.Awaitility.await;

import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.atomic.AtomicReference;
import java.util.stream.Collectors;

import javax.imageio.ImageIO;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.context.TestPropertySource;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.web.multipart.MultipartFile;

import com.study.backend.board.dto.common.request.BoardUpdateRequest;
import com.study.backend.board.exception.BoardNotFoundException;
import com.study.backend.board.model.Board;
import com.study.backend.board.model.BoardType;
import com.study.backend.common.IntegrationTestBase;
import com.study.backend.file.exception.FileException;

@TestPropertySource(properties = {
	"file.cleanup.enabled=false",
	"file.orphan.recovery-enabled=false",
	"free-board.path=free/",
	"free-board.deleted-path=free-deleted/",
	"gallery-board.picture.path=gallery/",
	"gallery-board.picture.deleted-path=gallery-deleted/",
	"gallery-board.thumbnail.path=thumbnail/"
})
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_CLASS)
class BoardFileConcurrencyTest extends IntegrationTestBase {

	@TempDir
	static Path storageRoot;

	@DynamicPropertySource
	static void storageProperties(DynamicPropertyRegistry registry) {
		registry.add("store.base-path", () -> storageRoot.toString());
	}

	@Autowired FreeBoardService freeBoardService;
	@Autowired GalleryService galleryService;
	@Autowired JdbcTemplate jdbc;
	@Autowired PlatformTransactionManager transactionManager;

	private Long memberId;
	private Long boardId;

	@BeforeEach
	void setUp() throws IOException {
		for (String directory : List.of(
			"free", "free-deleted",
			"gallery", "gallery-deleted", "thumbnail"
		)) {
			Files.createDirectories(storageRoot.resolve(directory));
		}

		String loginId = "race_" + UUID.randomUUID()
			.toString().replace("-", "").substring(0, 8);

		jdbc.update("""
            INSERT INTO member(
                member_id, member_password, member_name
            )
            VALUES (?, 'test-password', '테스터')
            """, loginId);

		memberId = jdbc.queryForObject(
			"SELECT id FROM member WHERE member_id = ?",
			Long.class,
			loginId
		);
	}

	@AfterEach
	void cleanUp() throws IOException {
		// 이 클래스의 임시 저장소에 생성한 파일과 정리 작업만 제거한다.
		for (String path : storedFiles()) {
			jdbc.update("""
                DELETE FROM file_cleanup_task
                WHERE source_path = ? OR destination_path = ?
                """, path, path);

			Files.deleteIfExists(storageRoot.resolve(path));
		}

		if (boardId != null) {
			jdbc.update(
				"DELETE FROM thumbnail WHERE board_id = ?", boardId
			);
			jdbc.update(
				"DELETE FROM file WHERE board_id = ?", boardId
			);
			jdbc.update(
				"DELETE FROM board WHERE id = ?", boardId
			);
		}

		if (memberId != null) {
			jdbc.update(
				"DELETE FROM member WHERE id = ?", memberId
			);
		}
	}

	@ParameterizedTest
	@EnumSource(
		value = BoardType.class,
		names = {"BOARDS", "GALLERIES"}
	)
	void updateFirst_deleteIncludesNewFileAndLatestThumbnail(
		BoardType type
	) throws Exception {
		createBoard(type);

		MultipartFile[] files = imageFiles("added.png");
		AtomicReference<String> addedPath = new AtomicReference<>();
		AtomicReference<String> latestThumbnail = new AtomicReference<>();

		String originalThumbnail =
			type == BoardType.GALLERIES ? thumbnailPath() : null;

		overlap(
			() -> {
				updatePost(type, files);

				addedPath.set(filePath("added.png"));

				if (type == BoardType.GALLERIES) {
					latestThumbnail.set(thumbnailPath());
				}
			},
			() -> deletePost(type)
		);

		assertBoardDeleted();
		assertMoveRegistered(type, addedPath.get());

		if (type == BoardType.GALLERIES) {
			assertThat(latestThumbnail.get())
				.isNotEqualTo(originalThumbnail);

			assertThat(count(
				"SELECT COUNT(*) FROM thumbnail WHERE board_id = ?",
				boardId
			)).isZero();

			assertThat(count("""
                SELECT COUNT(*)
                FROM file_cleanup_task
                WHERE task_type = 'DELETE'
                  AND source_path = ?
                  AND destination_path IS NULL
                """, latestThumbnail.get())).isEqualTo(1L);
		}
	}

	@ParameterizedTest
	@EnumSource(
		value = BoardType.class,
		names = {"BOARDS", "GALLERIES"}
	)
	void deleteFirst_updateIsRejectedWithoutNewFiles(
		BoardType type
	) throws Exception {
		createBoard(type);

		MultipartFile[] files = imageFiles("added.png");
		Set<String> filesBefore = storedFiles();

		assertThatThrownBy(() -> overlap(
			() -> deletePost(type),
			() -> updatePost(type, files)
		))
			.isInstanceOf(ExecutionException.class)
			.hasCauseInstanceOf(BoardNotFoundException.class);

		assertBoardDeleted();

		assertThat(count("""
            SELECT COUNT(*) FROM file
            WHERE board_id = ? AND file_name = 'added.png'
            """, boardId)).isZero();

		// 스케줄러를 껐으므로 기존 파일은 그대로이며,
		// 거절된 수정 요청이 신규 파일을 만들지 않아야 한다.
		assertThat(storedFiles()).isEqualTo(filesBefore);
	}

	@Test
	void deleteFirst_separateUploadIsRejected() throws Exception {
		// 자유게시판은 게시글만 먼저 커밋한 상태로 준비한다.
		createBoard(BoardType.BOARDS);

		MultipartFile[] files = imageFiles("added.png");
		Set<String> filesBefore = storedFiles();

		assertThatThrownBy(() -> overlap(
			() -> freeBoardService.deletePost(boardId, memberId),
			() -> freeBoardService.createFiles(boardId, files)
		))
			.isInstanceOf(ExecutionException.class)
			.hasCauseInstanceOf(FileException.class);

		assertBoardDeleted();

		assertThat(count(
			"SELECT COUNT(*) FROM file WHERE board_id = ?",
			boardId
		)).isZero();

		assertThat(storedFiles()).isEqualTo(filesBefore);
	}

	@Test
	void separateUploadFirst_deleteIncludesUploadedFile()
		throws Exception {

		createBoard(BoardType.BOARDS);

		MultipartFile[] files = imageFiles("added.png");
		AtomicReference<String> addedPath = new AtomicReference<>();

		overlap(
			() -> {
				freeBoardService.createFiles(boardId, files);
				addedPath.set(filePath("added.png"));
			},
			() -> freeBoardService.deletePost(boardId, memberId)
		);

		assertBoardDeleted();
		assertMoveRegistered(BoardType.BOARDS, addedPath.get());
	}

	private void createBoard(BoardType type) throws IOException {
		Board board = Board.builder()
			.title("동시성 테스트")
			.content("원래 내용")
			.categoryId(1L)
			.build();

		if (type == BoardType.BOARDS) {
			freeBoardService.createPost(board, type.id(), memberId);
		} else {
			galleryService.createPostWithFilesAndThumbnail(
				board, type.id(), memberId, imageFiles("original.png")
			);
		}

		boardId = board.getId();
	}

	private void updatePost(BoardType type, MultipartFile[] files) {
		BoardUpdateRequest request = BoardUpdateRequest.builder()
			.title("수정된 제목")
			.content("수정된 내용")
			.categoryId(1L)
			.version(0)
			.build();

		if (type == BoardType.BOARDS) {
			freeBoardService.updatePost(
				boardId, request, memberId, files
			);
		} else {
			galleryService.updatePost(
				boardId, request, memberId, files
			);
		}
	}

	private void deletePost(BoardType type) {
		if (type == BoardType.BOARDS) {
			freeBoardService.deletePost(boardId, memberId);
		} else {
			galleryService.deletePost(boardId, memberId);
		}
	}

	/**
	 * 첫 요청을 실행한 뒤 커밋을 보류한다.
	 * 두 번째 요청의 실제 DB 잠금 대기를 확인한 후
	 * 첫 요청을 커밋하고 두 번째 요청의 완료를 기다린다.
	 */
	private void overlap(Runnable first, Runnable second)
		throws Exception {

		ExecutorService executor = Executors.newFixedThreadPool(2);
		CountDownLatch firstFinished = new CountDownLatch(1);
		CountDownLatch allowCommit = new CountDownLatch(1);
		CountDownLatch secondStarted = new CountDownLatch(1);
		AtomicLong secondConnectionId = new AtomicLong();

		try {
			Future<?> firstFuture = executor.submit(() ->
				transaction().executeWithoutResult(status -> {
					first.run();
					firstFinished.countDown();
					awaitLatch(allowCommit);
				})
			);

			awaitLatch(firstFinished);

			Future<?> secondFuture = executor.submit(() ->
				transaction().executeWithoutResult(status -> {
					// 연결 ID 조회는 board/file의 읽기 스냅샷을 만들지 않는다.
					secondConnectionId.set(jdbc.queryForObject(
						"SELECT CONNECTION_ID()", Long.class
					));

					secondStarted.countDown();
					second.run();
				})
			);

			awaitLatch(secondStarted);

			await()
				.atMost(5, SECONDS)
				.pollInterval(50, MILLISECONDS)
				.until(() -> count("""
                    SELECT COUNT(*)
                    FROM information_schema.innodb_trx
                    WHERE trx_mysql_thread_id = ?
                      AND trx_state = 'LOCK WAIT'
                    """, secondConnectionId.get()) > 0);

			allowCommit.countDown();

			firstFuture.get(10, SECONDS);
			secondFuture.get(10, SECONDS);
		} finally {
			allowCommit.countDown();
			executor.shutdown();

			if (!executor.awaitTermination(10, SECONDS)) {
				executor.shutdownNow();
				throw new AssertionError(
					"동시성 테스트 작업이 종료되지 않았습니다."
				);
			}
		}
	}

	private TransactionTemplate transaction() {
		TransactionTemplate transaction =
			new TransactionTemplate(transactionManager);

		transaction.setIsolationLevel(
			TransactionDefinition.ISOLATION_REPEATABLE_READ
		);
		transaction.setTimeout(30);

		return transaction;
	}

	private void awaitLatch(CountDownLatch latch) {
		try {
			if (!latch.await(20, SECONDS)) {
				throw new AssertionError("테스트 동기화 시간 초과");
			}
		} catch (InterruptedException e) {
			Thread.currentThread().interrupt();
			throw new IllegalStateException(e);
		}
	}

	private void assertBoardDeleted() {
		assertThat(count("""
            SELECT COUNT(*) FROM board
            WHERE id = ? AND state = true
            """, boardId)).isEqualTo(1L);

		assertThat(count("""
            SELECT COUNT(*) FROM file
            WHERE board_id = ? AND state = false
            """, boardId)).isZero();
	}

	private void assertMoveRegistered(BoardType type, String source) {
		String directory = type == BoardType.BOARDS
			? "free-deleted"
			: "gallery-deleted";

		String destination = Path.of(
			directory, Path.of(source).getFileName().toString()
		).toString();

		assertThat(count("""
            SELECT COUNT(*)
            FROM file_cleanup_task
            WHERE task_type = 'MOVE'
              AND source_path = ?
              AND destination_path = ?
            """, source, destination)).isEqualTo(1L);
	}

	private String filePath(String fileName) {
		return jdbc.queryForObject("""
            SELECT CONCAT(path, store_name, extension)
            FROM file
            WHERE board_id = ? AND file_name = ?
            """, String.class, boardId, fileName);
	}

	private String thumbnailPath() {
		return jdbc.queryForObject("""
            SELECT CONCAT(path, store_name, extension)
            FROM thumbnail
            WHERE board_id = ?
            """, String.class, boardId);
	}

	private long count(String sql, Object... args) {
		return jdbc.queryForObject(sql, Long.class, args);
	}

	private Set<String> storedFiles() throws IOException {
		try (var paths = Files.walk(storageRoot)) {
			return paths
				.filter(Files::isRegularFile)
				.map(storageRoot::relativize)
				.map(Path::toString)
				.collect(Collectors.toSet());
		}
	}

	private MultipartFile[] imageFiles(String name) throws IOException {
		BufferedImage image =
			new BufferedImage(8, 8, BufferedImage.TYPE_INT_RGB);

		ByteArrayOutputStream output = new ByteArrayOutputStream();

		if (!ImageIO.write(image, "png", output)) {
			throw new IOException("테스트 PNG 생성 실패");
		}

		return new MultipartFile[] {
			new MockMultipartFile(
				"file", name, "image/png", output.toByteArray()
			)
		};
	}
}