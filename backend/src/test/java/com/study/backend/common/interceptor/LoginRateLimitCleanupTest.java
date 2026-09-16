package com.study.backend.common.interceptor;

import static org.assertj.core.api.Assertions.*;

import java.time.Instant;
import java.util.Map;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.test.util.ReflectionTestUtils;

import com.fasterxml.jackson.databind.ObjectMapper;

class LoginRateLimitCleanupTest {

	@ParameterizedTest
	@ValueSource(strings = {
		"loginAttempts",
		"signUpAttempts",
		"secretVerifyAttempts"
	})
	@DisplayName("모든 요청 기록이 만료된 항목만 제거하고 최근 기록이 있는 항목은 유지한다")
	@SuppressWarnings("unchecked")
	void evictExpiredEntries_removesOnlyFullyExpiredEntries(String fieldName) {
		LoginRateLimitInterceptor interceptor =
			new LoginRateLimitInterceptor(new ObjectMapper());

		Map<String, long[]> attempts =
			(Map<String, long[]>) ReflectionTestUtils.getField(
				interceptor,
				fieldName
			);

		assertThat(attempts).isNotNull();

		long now = Instant.now().toEpochMilli();
		long expired = now - 120_000L;

		long[] recentOnly = {now};
		long[] mixed = {expired, now};

		attempts.put("expired", new long[]{expired, expired - 1_000L});
		attempts.put("recent", recentOnly);
		attempts.put("mixed", mixed);

		interceptor.evictExpiredEntries();

		assertThat(attempts)
			.doesNotContainKey("expired")
			.containsOnlyKeys("recent", "mixed");

		assertThat(attempts.get("recent"))
			.containsExactly(now);

		assertThat(attempts.get("mixed"))
			.containsExactly(expired, now);
	}
}