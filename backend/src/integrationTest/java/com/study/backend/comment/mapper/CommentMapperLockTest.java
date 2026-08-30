package com.study.backend.comment.mapper;

import static java.util.concurrent.TimeUnit.*;
import static org.assertj.core.api.Assertions.*;

import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeoutException;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

import com.study.backend.board.model.Board;
import com.study.backend.comment.model.Comment;
import com.study.backend.common.IntegrationTestBase;

class CommentMapperLockTest extends IntegrationTestBase {

	@Autowired
	CommentMapper commentMapper;

	@Autowired
	JdbcTemplate jdbcTemplate;

	@Autowired
	PlatformTransactionManager transactionManager;

	private Long memberId;
	private Long boardId;

	@BeforeEach
	void setUp() {
		String suffix = UUID.randomUUID()
			.toString()
			.replace("-", "")
			.substring(0, 8);

		String loginId = "lock_" + suffix;
		String title = "댓글 락 테스트 " + suffix;

		jdbcTemplate.update(
			"""
			INSERT INTO member(
				member_id,
				member_password,
				member_name
			)
			VALUES (?, ?, ?)
			""",
			loginId,
			"password",
			"락테스터"
		);

		memberId = jdbcTemplate.queryForObject(
			"SELECT id FROM member WHERE member_id = ?",
			Long.class,
			loginId
		);

		jdbcTemplate.update(
			"""
			INSERT INTO board(
				title,
				content,
				board_type_id,
				category_id,
				member_id,
				state
			)
			VALUES (?, ?, 2, 1, ?, false)
			""",
			title,
			"내용",
			memberId
		);

		boardId = jdbcTemplate.queryForObject(
			"""
			SELECT id
			FROM board
			WHERE member_id = ?
			  AND title = ?
			""",
			Long.class,
			memberId,
			title
		);
	}

	@AfterEach
	void tearDown() {
		if (boardId != null) {
			jdbcTemplate.update(
				"DELETE FROM comment WHERE board_id = ?",
				boardId
			);

			jdbcTemplate.update(
				"DELETE FROM board WHERE id = ?",
				boardId
			);
		}

		if (memberId != null) {
			jdbcTemplate.update(
				"DELETE FROM member WHERE id = ?",
				memberId
			);
		}
	}

	@Test
	@DisplayName("댓글 등록 트랜잭션의 공유 잠금은 게시글 삭제를 대기시킨다")
	void createComment_shareLock_blocksBoardDeleteUntilCommit()
		throws Exception {

		ExecutorService executor =
			Executors.newFixedThreadPool(2);

		CountDownLatch lockAcquired = new CountDownLatch(1);
		CountDownLatch finishComment = new CountDownLatch(1);
		CountDownLatch deleteStarted = new CountDownLatch(1);

		try {
			Future<Void> commentFuture = executor.submit(() -> {
				TransactionTemplate transaction =
					new TransactionTemplate(transactionManager);

				transaction.executeWithoutResult(status -> {
					Board board =
						commentMapper.getBoardForComment(boardId);

					if (board == null) {
						throw new AssertionError(
							"테스트 게시글이 존재하지 않습니다."
						);
					}

					// FOR SHARE 획득 완료
					lockAcquired.countDown();

					// 삭제 요청이 실행될 때까지 트랜잭션 유지
					await(finishComment);

					Comment comment = Comment.builder()
						.content("동시성 테스트 댓글")
						.boardId(boardId)
						.memberId(memberId)
						.build();

					commentMapper.createComment(comment);
				});

				return null;
			});

			assertThat(lockAcquired.await(3, SECONDS))
				.isTrue();

			Future<Integer> deleteFuture = executor.submit(() -> {
				TransactionTemplate transaction =
					new TransactionTemplate(transactionManager);

				return transaction.execute(status -> {
					deleteStarted.countDown();

					return jdbcTemplate.update(
						"""
						UPDATE board
						SET state = true
						WHERE id = ?
						""",
						boardId
					);
				});
			});

			assertThat(deleteStarted.await(3, SECONDS))
				.isTrue();

			// FOR SHARE가 정상이라면 UPDATE는 아직 완료되지 않아야 함
			assertThatThrownBy(() ->
				deleteFuture.get(500, MILLISECONDS)
			).isInstanceOf(TimeoutException.class);

			// 댓글 INSERT 및 트랜잭션 커밋 허용
			finishComment.countDown();

			commentFuture.get(3, SECONDS);

			// 공유 잠금이 해제된 후 게시글 삭제 완료
			assertThat(deleteFuture.get(3, SECONDS))
				.isEqualTo(1);

			Integer commentCount = jdbcTemplate.queryForObject(
				"""
				SELECT COUNT(*)
				FROM comment
				WHERE board_id = ?
				""",
				Integer.class,
				boardId
			);

			Integer boardState = jdbcTemplate.queryForObject(
				"""
				SELECT state
				FROM board
				WHERE id = ?
				""",
				Integer.class,
				boardId
			);

			assertThat(commentCount).isEqualTo(1);
			assertThat(boardState).isEqualTo(1);

		} finally {
			// 테스트 실패 시에도 잠금이 풀리도록 보장
			finishComment.countDown();

			executor.shutdown();

			if (!executor.awaitTermination(3, SECONDS)) {
				executor.shutdownNow();
			}
		}
	}

	private void await(CountDownLatch latch) {
		try {
			if (!latch.await(3, SECONDS)) {
				throw new AssertionError(
					"트랜잭션 대기 시간이 초과됐습니다."
				);
			}
		} catch (InterruptedException e) {
			Thread.currentThread().interrupt();
			throw new IllegalStateException(e);
		}
	}
}