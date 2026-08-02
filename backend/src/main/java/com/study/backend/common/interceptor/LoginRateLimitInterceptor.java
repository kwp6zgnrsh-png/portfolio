package com.study.backend.common.interceptor;

import java.time.Instant;
import java.util.Arrays;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.HandlerInterceptor;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.study.backend.common.dto.ApiResponse;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.NonNull;

/**
 * 로그인 엔드포인트 브루트포스 방지용 슬라이딩 윈도우 Rate Limiter.
 * IP당 1분에 최대 10회 시도를 허용하며, 초과 시 429를 반환한다.
 */
@Component
public class LoginRateLimitInterceptor implements HandlerInterceptor {

	private static final String TOO_MANY_REQUESTS_MESSAGE = "요청이 너무 많습니다. 잠시 후 다시 시도해주세요.";

	/** 1분 내 허용 최대 로그인 시도 횟수 */
	private static final int LOGIN_MAX_ATTEMPTS = 10;

	/** 1분 내 허용 최대 비밀글 비밀번호 실패 횟수 */
	private static final int SECRET_VERIFY_MAX_FAILED_ATTEMPTS = 5;

	/** 슬라이딩 윈도우 크기 (밀리초 단위, 1분) */
	private static final long WINDOW_MS = 60_000L;

	private static final String LOGIN_PATH = "/api/login";
	private static final String SECRET_VERIFY_SUFFIX = "/verifySecretPostPassword";

	/** 로그인: IP별 요청 타임스탬프 */
	private final Map<String, long[]> loginAttempts = new ConcurrentHashMap<>();

	/** 비밀글 비밀번호 검증: IP + boardId별 실패 타임스탬프 */
	private final Map<String, long[]> secretVerifyFailedAttempts = new ConcurrentHashMap<>();

	private final ObjectMapper objectMapper;

	public LoginRateLimitInterceptor(ObjectMapper objectMapper) {
		this.objectMapper = objectMapper;
	}

	/** 1분마다 실행: 윈도우가 만료된 IP 항목을 메모리에서 제거 */
	@Scheduled(fixedRate = 60_000)
	public void evictExpiredEntries() {
		long now = Instant.now().toEpochMilli();
		// 모든 타임스탬프가 1분 이상 지난 항목은 삭제
		loginAttempts.entrySet().removeIf(entry ->
			Arrays.stream(entry.getValue()).allMatch(t -> now - t >= WINDOW_MS)
		);
		secretVerifyFailedAttempts.entrySet().removeIf(entry ->
			Arrays.stream(entry.getValue()).allMatch(t -> now - t >= WINDOW_MS)
		);
	}


	 /** 로그인 및 비밀글 검증 요청에 대해 슬라이딩 윈도우 기반 Rate Limit을 적용한다.*/
	@Override
	public boolean preHandle(@NonNull HttpServletRequest request, @NonNull HttpServletResponse response,
							 @NonNull Object handler) throws Exception {
		String ip = resolveClientIp(request);
		long now = Instant.now().toEpochMilli();

		if (isLoginPath(request)) {
			long[] current = recordAttempt(loginAttempts, ip, now);
			// 윈도우 내 시도 횟수가 최대치를 초과하면 429 반환
			if (current.length > LOGIN_MAX_ATTEMPTS) {
				writeTooManyRequests(response);
				return false;
			}
			return true;
		}

		if (isSecretVerifyPath(request)) {
			String boardId = extractBoardIdFromSecretVerifyPath(request);
			if (boardId == null) {
				return true;
			}
			String attemptKey = buildSecretAttemptKey(ip, boardId);
			long[] currentFailures = filterRecentAttempts(secretVerifyFailedAttempts, attemptKey, now);
			// 실패 횟수 기준으로 차단한다. 성공 응답은 afterCompletion에서 실패 카운트를 초기화한다.
			if (currentFailures.length >= SECRET_VERIFY_MAX_FAILED_ATTEMPTS) {
				writeTooManyRequests(response);
				return false;
			}
		}

		return true;
	}

	/** 비밀글 검증 결과에 따라 해당 IP + 게시글의 실패 카운트를 기록하거나 초기화한다. */
	@Override
	public void afterCompletion(@NonNull HttpServletRequest request, @NonNull HttpServletResponse response,
		@NonNull Object handler, Exception ex) {
		int status = response.getStatus();

		if (isLoginPath(request)) {
			return;
		}

		if (!isSecretVerifyPath(request)) {
			return;
		}

		String boardId = extractBoardIdFromSecretVerifyPath(request);
		if (boardId == null) {
			return;
		}

		String attemptKey = buildSecretAttemptKey(resolveClientIp(request), boardId);

		if (status == HttpStatus.FORBIDDEN.value()) {
			recordAttempt(secretVerifyFailedAttempts, attemptKey, Instant.now().toEpochMilli());
			return;
		}
		if (isSuccessStatus(status)) {
			secretVerifyFailedAttempts.remove(attemptKey);
		}
	}

	/** HTTP 응답 상태가 성공 범위(2xx)인지 확인한다. */
	private boolean isSuccessStatus(int status) {
		return status >= 200 && status < 300;
	}

	/** 현재 nginx 등 리버스 프록시를 사용하지 않으므로 RemoteAddr로 실제 클라이언트 IP를 직접 가져옴 */
	private String resolveClientIp(HttpServletRequest request) {
		return request.getRemoteAddr();
	}

	/** 요청 URI가 로그인 경로인지 확인한다. */
	private boolean isLoginPath(HttpServletRequest request) {
		return LOGIN_PATH.equals(request.getRequestURI());
	}

	/** 요청 URI가 비밀글 비밀번호 검증 경로인지 확인한다. */
	private boolean isSecretVerifyPath(HttpServletRequest request) {
		String uri = request.getRequestURI();
		return uri != null && uri.endsWith(SECRET_VERIFY_SUFFIX);
	}

	/** 비밀글 검증 URI에서 boardId 세그먼트를 추출한다. */
	private String extractBoardIdFromSecretVerifyPath(HttpServletRequest request) {
		String uri = request.getRequestURI();
		if (uri == null) {
			return null;
		}
		String[] segments = uri.split("/");
		// .../{boardType}/{boardId}/verifySecretPostPassword
		if (segments.length < 2) {
			return null;
		}
		return segments[segments.length - 2];
	}

	/** IP와 boardId를 조합하여 Rate Limit 키를 생성한다. */
	private String buildSecretAttemptKey(String ip, String boardId) {
		return ip + ":" + boardId;
	}

	/** 슬라이딩 윈도우 내 기존 기록에 현재 시각을 추가하고 반환한다. */
	private long[] recordAttempt(Map<String, long[]> attempts, String key, long now) {
		return attempts.compute(key, (k, timestamps) -> {
			if (timestamps == null) {
				return new long[] { now };
			}
			long[] recent = Arrays.stream(timestamps)
				.filter(t -> now - t < WINDOW_MS)
				.toArray();
			long[] updated = new long[recent.length + 1];
			System.arraycopy(recent, 0, updated, 0, recent.length);
			updated[recent.length] = now;
			return updated;
		});
	}

	/** 슬라이딩 윈도우 밖의 기록을 제거하고 남은 기록만 반환한다. */
	private long[] filterRecentAttempts(Map<String, long[]> attempts, String key, long now) {
		return attempts.compute(key, (k, timestamps) -> {
			if (timestamps == null) {
				return new long[0];
			}
			return Arrays.stream(timestamps)
				.filter(t -> now - t < WINDOW_MS)
				.toArray();
		});
	}

	/** 429 Too Many Requests 응답을 JSON으로 작성한다. */
	private void writeTooManyRequests(HttpServletResponse response) throws Exception {
		response.setStatus(HttpStatus.TOO_MANY_REQUESTS.value());
		response.setContentType(MediaType.APPLICATION_JSON_VALUE);
		response.setCharacterEncoding("UTF-8");
		ApiResponse<Void> apiResponse = ApiResponse.of(TOO_MANY_REQUESTS_MESSAGE);
		objectMapper.writeValue(response.getWriter(), apiResponse);
	}
}
