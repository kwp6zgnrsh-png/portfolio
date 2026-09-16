package com.study.backend.common.util;

import static java.util.concurrent.TimeUnit.*;
import static org.assertj.core.api.Assertions.*;
import static org.awaitility.Awaitility.await;

import java.sql.Connection;
import java.sql.SQLTimeoutException;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeoutException;
import java.util.concurrent.atomic.AtomicReference;

import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.mapping.Environment;
import org.apache.ibatis.session.Configuration;
import org.apache.ibatis.session.ExecutorType;
import org.apache.ibatis.session.SqlSessionFactory;
import org.apache.ibatis.session.SqlSessionFactoryBuilder;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;
import org.mybatis.spring.MyBatisExceptionTranslator;
import org.mybatis.spring.SqlSessionTemplate;
import org.mybatis.spring.transaction.SpringManagedTransactionFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.dao.QueryTimeoutException;
import org.springframework.jdbc.CannotGetJdbcConnectionException;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.TestPropertySource;

import com.study.backend.common.IntegrationTestBase;
import com.study.backend.common.exception.BoardQueryUnavailableException;
import com.zaxxer.hikari.HikariDataSource;

@TestPropertySource(properties = {
	"spring.datasource.hikari.maximum-pool-size=1",
	"spring.datasource.hikari.minimum-idle=1",
	"spring.sql.init.mode=never",
	"file.cleanup.enabled=false",
	"file.orphan.recovery-enabled=false"
})
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_CLASS)
@Timeout(15)
class BoardQueryTimeoutIntegrationTest extends IntegrationTestBase {

	@Autowired HikariDataSource pool;
	@Autowired SqlSessionFactory applicationSqlSessionFactory;

	private ExecutorService worker;
	private ProbeMapper probe;

	@BeforeEach
	void setUp() throws Exception {
		// 테스트에서 타임아웃을 덮어쓰지 않고 실제 설정이 적용됐는지 확인한다.
		assertThat(pool.getConnectionTimeout()).isEqualTo(1000L);
		assertThat(pool.getValidationTimeout()).isEqualTo(500L);
		assertThat(String.valueOf(pool.getDataSourceProperties().get("connectTimeout")))
			.isEqualTo("1000");
		assertThat(String.valueOf(pool.getDataSourceProperties().get("socketTimeout")))
			.isEqualTo("5000");
		assertThat(String.valueOf(pool.getDataSourceProperties().get("enableQueryTimeouts")))
			.isEqualTo("true");

		Configuration actual = applicationSqlSessionFactory.getConfiguration();
		List<String> statementIds = List.of(
			"com.study.backend.board.mapper.NoticeMapper.getPostList",
			"com.study.backend.board.mapper.FreeBoardMapper.getPostList",
			"com.study.backend.board.mapper.GalleryMapper.getPostList",
			"com.study.backend.board.mapper.InquiryMapper.getPostList",
			"com.study.backend.comment.mapper.CommentMapper.getCommentsByBoardId",
			"com.study.backend.file.mapper.FileMapper.getFilesByBoardId"
		);
		for (String id : statementIds) {
			assertThat(actual.getMappedStatement(id).getTimeout())
				.as(id + " timeout")
				.isEqualTo(2);
		}

		// 실제 매퍼의 제한 시간을 가져와 테스트용 SQL에 적용한다.
		Configuration testConfig = new Configuration(new Environment(
			"timeout-probe", new SpringManagedTransactionFactory(), pool
		));
		testConfig.setDefaultStatementTimeout(
			actual.getMappedStatement(statementIds.get(0)).getTimeout()
		);
		testConfig.addMapper(ProbeMapper.class);
		SqlSessionFactory factory = new SqlSessionFactoryBuilder().build(testConfig);

		// 예외 변환용 DB 메타데이터는 연결을 점유하기 전에 미리 읽는다.
		probe = new SqlSessionTemplate(
			factory, ExecutorType.SIMPLE, new MyBatisExceptionTranslator(pool, false)
		).getMapper(ProbeMapper.class);

		worker = Executors.newSingleThreadExecutor();
		assertThat(worker.submit(() -> probe.ping()).get(3, SECONDS)).isEqualTo(1);
		assertPoolReleased();
	}

	@AfterEach
	void tearDown() throws Exception {
		if (worker != null) {
			worker.shutdownNow();
			assertThat(worker.awaitTermination(6, SECONDS)).isTrue();
		}
	}

	@Test
	@DisplayName("A: SQL 제한 시간이 느린 쿼리를 종료하고 자원을 반환한다")
	void sqlTimeout_releasesWorkerAndConnection() throws Exception {
		// SQL 제한(2초)이 먼저 발생하도록 응답 대기는 길게 둔다.
		BoardQueryRunner runner = new BoardQueryRunner(worker, 8000);
		CompletableFuture<Integer> result = runner.submit(probe::slow);

		Throwable failure = catchThrowable(() -> runner.awaitAll(result));

		assertThat(failure).isInstanceOf(BoardQueryUnavailableException.class);
		assertThat(isSqlTimeout(failure)).isTrue();
		assertThat(hasCause(failure, TimeoutException.class)).isFalse();

		assertWorkerAndConnectionReusable();
	}

	@Test
	@DisplayName("B: 응답이 먼저 시간 초과돼도 SQL 종료 후 자원이 반환된다")
	void responseTimeout_doesNotLeaveWorkerOccupied() throws Exception {
		// SQL 제한 2초보다 짧게 설정한다.
		BoardQueryRunner runner = new BoardQueryRunner(worker, 300);
		CountDownLatch started = new CountDownLatch(1);
		CountDownLatch finished = new CountDownLatch(1);
		AtomicReference<Throwable> sqlFailure = new AtomicReference<>();

		CompletableFuture<Integer> result = runner.submit(() -> {
			started.countDown();
			try {
				return probe.slow();
			} catch (RuntimeException e) {
				sqlFailure.set(e);
				throw e;
			} finally {
				finished.countDown();
			}
		});

		assertThat(started.await(2, SECONDS)).isTrue();
		Throwable failure = catchThrowable(() -> runner.awaitAll(result));

		assertThat(failure).isInstanceOf(BoardQueryUnavailableException.class);
		assertThat(hasCause(failure, TimeoutException.class)).isTrue();
		// 응답만 끝났고 실제 조회는 아직 실행 중임을 확인한다.
		assertThat(finished.getCount()).isEqualTo(1L);
		assertThat(pool.getHikariPoolMXBean().getActiveConnections()).isEqualTo(1);

		// 원래 SLEEP(10)이 끝나거나 소켓 제한(5초)이 올 때까지 기다리면 실패한다.
		assertThat(finished.await(4, SECONDS)).isTrue();
		assertThat(isSqlTimeout(sqlFailure.get())).isTrue();
		assertWorkerAndConnectionReusable();
	}

	@Test
	@DisplayName("C: 연결 풀 고갈 시 연결 대기가 끝나고 이후 조회가 회복된다")
	void exhaustedPool_releasesWorkerAndRecovers() throws Exception {
		BoardQueryRunner runner = new BoardQueryRunner(worker, 8000);

		// 유일한 연결을 테스트 스레드가 점유한다.
		try (Connection held = pool.getConnection()) {
			assertThat(held.isClosed()).isFalse();
			CompletableFuture<Integer> result = runner.submit(probe::ping);
			Throwable failure = catchThrowable(() -> runner.awaitAll(result));

			assertThat(failure).isInstanceOf(BoardQueryUnavailableException.class);
			assertThat(hasCause(failure, CannotGetJdbcConnectionException.class)).isTrue();
			assertThat(hasCause(failure, TimeoutException.class)).isFalse();

			// DB가 필요 없는 후속 작업으로 작업 스레드 반환을 확인한다.
			assertThat(worker.submit(() -> 42).get(2, SECONDS)).isEqualTo(42);
			assertThat(pool.getHikariPoolMXBean().getThreadsAwaitingConnection()).isZero();
			assertThat(pool.getHikariPoolMXBean().getActiveConnections()).isEqualTo(1);
		}

		// 테스트가 점유했던 연결을 반환한 뒤 SQL도 다시 실행돼야 한다.
		assertWorkerAndConnectionReusable();
	}

	private void assertWorkerAndConnectionReusable() throws Exception {
		assertPoolReleased();
		assertThat(worker.submit(() -> probe.ping()).get(3, SECONDS)).isEqualTo(1);
		assertPoolReleased();
	}

	private void assertPoolReleased() {
		await().atMost(2, SECONDS).untilAsserted(() -> {
			assertThat(pool.getHikariPoolMXBean().getActiveConnections()).isZero();
			assertThat(pool.getHikariPoolMXBean().getThreadsAwaitingConnection()).isZero();
		});
	}

	private static boolean isSqlTimeout(Throwable error) {
		return hasCause(error, SQLTimeoutException.class)
			|| hasCause(error, QueryTimeoutException.class);
	}

	private static boolean hasCause(Throwable error, Class<? extends Throwable> type) {
		for (Throwable cause = error; cause != null; cause = cause.getCause()) {
			if (type.isInstance(cause)) {
				return true;
			}
		}
		return false;
	}

	public interface ProbeMapper {
		@Select("SELECT SLEEP(10)")
		int slow();

		@Select("SELECT 1")
		int ping();
	}
}
