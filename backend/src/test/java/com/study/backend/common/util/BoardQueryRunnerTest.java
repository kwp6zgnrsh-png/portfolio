package com.study.backend.common.util;

import static org.assertj.core.api.Assertions.*;

import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executor;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.TimeoutException;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;

import com.study.backend.common.exception.BoardQueryUnavailableException;

@Timeout(5)
class BoardQueryRunnerTest {

	@Test
	@DisplayName("정상 조회 결과를 반환한다")
	void submit_success_returnsResult() {
		BoardQueryRunner runner = new BoardQueryRunner(Runnable::run, 3000);

		CompletableFuture<String> future = runner.submit(() -> "조회 결과");

		runner.awaitAll(future);

		assertThat(future.join()).isEqualTo("조회 결과");
	}

	@Test
	@DisplayName("작업 제출 거절은 조회 불가 예외로 전달된다")
	void submit_rejected_throwsUnavailable() {
		Executor rejectingExecutor = command -> {
			throw new RejectedExecutionException("테스트용 거절");
		};
		BoardQueryRunner runner =
			new BoardQueryRunner(rejectingExecutor, 3000);

		// submit 자체는 던지지 않고 실패한 Future를 반환해야 한다.
		CompletableFuture<String> future = runner.submit(() -> "실행되지 않음");

		assertThatThrownBy(() -> runner.awaitAll(future))
			.isInstanceOf(BoardQueryUnavailableException.class)
			.hasCauseInstanceOf(RejectedExecutionException.class);
	}

	@Test
	@DisplayName("완료되지 않은 작업은 제한 시간 후 조회 불가 예외가 된다")
	void submit_timeout_throwsUnavailable() {
		Executor neverRunsExecutor = command -> {
			// 작업이 대기열에서 실행되지 않는 상황을 재현
		};

		BoardQueryRunner runner =
			new BoardQueryRunner(neverRunsExecutor, 50);

		CompletableFuture<String> future = runner.submit(() -> "실행되지 않음");

		assertThatThrownBy(() -> runner.awaitAll(future))
			.isInstanceOf(BoardQueryUnavailableException.class)
			.hasCauseInstanceOf(TimeoutException.class);
	}

	@Test
	@DisplayName("일반 조회 예외는 조회 불가 예외로 바꾸지 않는다")
	void submit_regularFailure_preservesOriginalException() {
		BoardQueryRunner runner = new BoardQueryRunner(Runnable::run, 3000);
		IllegalStateException original = new IllegalStateException("일반 조회 오류");

		CompletableFuture<String> future = runner.submit(() -> { throw original; });

		assertThatThrownBy(() -> runner.awaitAll(future))
			.isSameAs(original);
	}
}