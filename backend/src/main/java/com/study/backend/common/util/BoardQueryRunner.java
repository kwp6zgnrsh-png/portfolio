package com.study.backend.common.util;

import java.sql.SQLTimeoutException;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;
import java.util.concurrent.Executor;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.function.Supplier;

import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.dao.QueryTimeoutException;
import org.springframework.jdbc.CannotGetJdbcConnectionException;
import org.springframework.stereotype.Component;

import com.study.backend.common.exception.BoardQueryUnavailableException;

@Component
public class BoardQueryRunner {

	private final Executor executor;
	private final long timeoutMs;

	public BoardQueryRunner(@Qualifier("boardQueryExecutor") Executor executor,
							@Value("${async.query-timeout-ms:3000}") long timeoutMs){

		if (timeoutMs <= 0){
			throw new IllegalArgumentException("조회 대기시간은 0보다 커야 합니다.");
		}

		this.executor = executor;
		this.timeoutMs = timeoutMs;
	}

	/** 작업 거절과 시간 초과를 실패한 Future로 전달한다. */
	public <T>CompletableFuture<T> submit(Supplier<T> query){
		try{
			return CompletableFuture
				.supplyAsync(query, executor)
				.orTimeout(timeoutMs, TimeUnit.MILLISECONDS)
				.handle((result, error) -> {
					if (error == null) {
						return result;
					}

					Throwable cause = unwrap(error);

					if (isQueryUnavailable(cause)) {
						throw new BoardQueryUnavailableException(cause);
					}

					throw new CompletionException(cause);
				});
		} catch (RejectedExecutionException e) {
			return CompletableFuture.failedFuture(new BoardQueryUnavailableException(e));
		}
	}

	/** 비동기 예외의 포장을 벗겨 기존 예외 처리기로 전달한다. */
	public void awaitAll(CompletableFuture<?>... futures) {
		try {
			CompletableFuture.allOf(futures).join();
		} catch (CompletionException e) {
			Throwable cause = unwrap(e);

			if (cause instanceof RuntimeException runtimeException) {
				throw runtimeException;
			}

			throw new CompletionException(cause);
		}
	}

	private static boolean isQueryUnavailable(Throwable error) {
		for (Throwable cause = error;
		     cause != null;
		     cause = cause.getCause()) {

			if (cause instanceof TimeoutException
				|| cause instanceof QueryTimeoutException
				|| cause instanceof SQLTimeoutException
				|| cause instanceof CannotGetJdbcConnectionException) {
				return true;
			}
		}

		return false;
	}

	private static Throwable unwrap(Throwable error) {
		while (error instanceof CompletionException && error.getCause() != null) {
			error = error.getCause();
		}
		return error;
	}
}
