package com.study.backend.common.config;

import java.util.concurrent.Executor;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;

/* 게시판 목록 조회용 비동기 스레드 풀.
* 풀 설정은 application.yml의 async.executor 참조.
*/
@Configuration
public class AsyncConfig {

	@Value("${async.executor.core-size}")
	private int coreSize;

	@Value("${async.executor.max-size}")
	private int maxSize;

	@Value("${async.executor.queue-capacity}")
	private int queueCapacity;

	@Bean
	public Executor boardQueryExecutor() {
		ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();
		executor.setCorePoolSize(coreSize);
		executor.setMaxPoolSize(maxSize);
		executor.setQueueCapacity(queueCapacity);
		executor.setThreadNamePrefix("board-query-");
		executor.initialize();
		return executor;
	}
}
