package com.study.backend.board.strategy;

import static org.assertj.core.api.Assertions.*;

import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.lang.reflect.InvocationTargetException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import javax.imageio.ImageIO;

import org.apache.ibatis.executor.Executor;
import org.apache.ibatis.mapping.MappedStatement;
import org.apache.ibatis.plugin.Interceptor;
import org.apache.ibatis.plugin.Intercepts;
import org.apache.ibatis.plugin.Invocation;
import org.apache.ibatis.plugin.Signature;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.context.TestPropertySource;
import org.springframework.web.multipart.MultipartFile;

import com.study.backend.board.dto.common.BoardCreateResult;
import com.study.backend.board.model.Board;
import com.study.backend.board.model.BoardType;
import com.study.backend.common.IntegrationTestBase;
import com.study.backend.file.model.FileMetaData;

@TestPropertySource(properties = {
	"file.cleanup.enabled=false",
	"file.orphan.recovery-enabled=false",
	"free-board.path=free/",
	"free-board.deleted-path=free-deleted/"
})
@Import(FreeBoardPartialSuccessIntegrationTest.FailureConfig.class)
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_CLASS)
class FreeBoardPartialSuccessIntegrationTest extends IntegrationTestBase {

	@TempDir
	static Path storageRoot;

	@DynamicPropertySource
	static void storageProperties(DynamicPropertyRegistry registry) {
		registry.add("store.base-path", () -> storageRoot.toString());
	}

	@Autowired FreeBoardStrategy strategy;
	@Autowired JdbcTemplate jdbc;
	@Autowired FailSecondFileInsert failureInjector;

	private Long memberId;
	private Board board;

	@BeforeEach
	void setUp() throws Exception {
		Files.createDirectories(storageRoot.resolve("free"));
		Files.createDirectories(storageRoot.resolve("free-deleted"));

		String loginId = "partial_" + UUID.randomUUID()
			.toString()
			.replace("-", "")
			.substring(0, 8);

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

		board = Board.builder()
			.title("파일 DB 오류 부분 성공 테스트")
			.content("게시글은 유지되어야 한다.")
			.categoryId(1L)
			.build();
	}

	@AfterEach
	void cleanUp() throws Exception {
		// 검증이 끝난 뒤 이 테스트가 추적한 파일과 정리 작업만 제거한다.
		for (Path path : failureInjector.writtenPaths) {
			String relativePath = storageRoot
				.relativize(path)
				.toString();

			jdbc.update("""
                DELETE FROM file_cleanup_task
                WHERE source_path = ? OR destination_path = ?
                """, relativePath, relativePath);

			Files.deleteIfExists(path);
		}

		if (board != null && board.getId() != null) {
			jdbc.update(
				"DELETE FROM file WHERE board_id = ?",
				board.getId()
			);
			jdbc.update(
				"DELETE FROM board WHERE id = ?",
				board.getId()
			);
		}

		if (memberId != null) {
			jdbc.update(
				"DELETE FROM member WHERE id = ?",
				memberId
			);
		}
	}

	@Test
	@DisplayName("두 번째 파일 DB 저장 실패 시 게시글은 유지하고 파일 전체를 롤백·정리한다")
	void secondFileInsertFails_returnsPartialSuccessAndCleansFiles()
		throws Exception {

		MultipartFile[] files = {
			imageFile("first.png"),
			imageFile("second.png")
		};

		// 실제 Strategy와 트랜잭션 프록시를 거쳐 등록한다.
		BoardCreateResult result = strategy.createPost(
			board,
			BoardType.BOARDS.id(),
			memberId,
			files
		);

		// 1. 파일 실패를 알리는 부분 성공 결과
		assertThat(result.fileUploadFailed()).isTrue();
		assertThat(result.message())
			.isEqualTo("게시글은 등록됐지만 파일 업로드에 실패했습니다.");

		// 2. 의도한 시점까지 실제로 실행됐는지 확인
		assertThat(failureInjector.insertAttempts).isEqualTo(2);
		assertThat(failureInjector.firstFileRowsBeforeFailure)
			.isEqualTo(1L);
		assertThat(failureInjector.databaseFailure)
			.isInstanceOf(SQLException.class);

		// 3. 게시글은 별도 트랜잭션으로 커밋되어 유지됨
		assertThat(board.getId()).isNotNull();
		assertThat(jdbc.queryForObject("""
            SELECT COUNT(*)
            FROM board
            WHERE id = ? AND state = false
            """, Long.class, board.getId()))
			.isEqualTo(1L);

		// 4. 먼저 INSERT된 첫 번째 파일도 함께 롤백됨
		assertThat(jdbc.queryForObject("""
            SELECT COUNT(*)
            FROM file
            WHERE board_id = ?
            """, Long.class, board.getId()))
			.isZero();

		// 5. DB 실패 전에 실제로 생성됐던 두 물리 파일도 정리됨
		assertThat(failureInjector.writtenPaths).hasSize(2);
		for (Path path : failureInjector.writtenPaths) {
			assertThat(path).doesNotExist();
		}

		try (var paths = Files.walk(storageRoot)) {
			assertThat(paths.filter(Files::isRegularFile).toList())
				.isEmpty();
		}
	}

	private MockMultipartFile imageFile(String name) throws Exception {
		BufferedImage image =
			new BufferedImage(8, 8, BufferedImage.TYPE_INT_RGB);

		ByteArrayOutputStream output = new ByteArrayOutputStream();
		assertThat(ImageIO.write(image, "png", output)).isTrue();

		return new MockMultipartFile(
			"file",
			name,
			"image/png",
			output.toByteArray()
		);
	}

	@TestConfiguration(proxyBeanMethods = false)
	static class FailureConfig {

		@Bean
		FailSecondFileInsert failSecondFileInsert(JdbcTemplate jdbc) {
			return new FailSecondFileInsert(jdbc);
		}
	}

	@Intercepts({
		@Signature(
			type = Executor.class,
			method = "update",
			args = {MappedStatement.class, Object.class}
		)
	})
	static class FailSecondFileInsert implements Interceptor {

		private static final String TARGET_STATEMENT =
			"com.study.backend.file.mapper.FileMapper.createFile";

		private final JdbcTemplate jdbc;

		private int insertAttempts;
		private Long firstFileRowsBeforeFailure;
		private Throwable databaseFailure;
		private final List<Path> writtenPaths = new ArrayList<>();

		FailSecondFileInsert(JdbcTemplate jdbc) {
			this.jdbc = jdbc;
		}

		@Override
		public Object intercept(Invocation invocation) throws Throwable {
			MappedStatement statement =
				(MappedStatement) invocation.getArgs()[0];

			if (!TARGET_STATEMENT.equals(statement.getId())) {
				return invocation.proceed();
			}

			FileMetaData file =
				(FileMetaData) invocation.getArgs()[1];

			insertAttempts++;

			Path physicalFile = storageRoot
				.resolve(file.getPath())
				.resolve(file.getStoreName() + file.getExtension());

			writtenPaths.add(physicalFile);

			// DB INSERT 전에 디스크 저장이 완료됐는지 확인한다.
			assertThat(physicalFile).isRegularFile();

			if (insertAttempts != 2) {
				return invocation.proceed();
			}

			// 같은 파일 트랜잭션에서 첫 번째 INSERT가 반영됐는지 확인한다.
			firstFileRowsBeforeFailure = jdbc.queryForObject("""
                SELECT COUNT(*)
                FROM file
                WHERE board_id = ?
                """, Long.class, file.getBoardId());

			assertThat(firstFileRowsBeforeFailure).isEqualTo(1L);
			assertThat(writtenPaths).hasSize(2);
			for (Path path : writtenPaths) {
				assertThat(path).isRegularFile();
			}

			// 실제 DB의 NOT NULL 제약조건 오류를 유발한다.
			file.setFileName(null);

			try {
				return invocation.proceed();
			} catch (InvocationTargetException e) {
				databaseFailure = e.getCause();
				throw e;
			}
		}
	}
}