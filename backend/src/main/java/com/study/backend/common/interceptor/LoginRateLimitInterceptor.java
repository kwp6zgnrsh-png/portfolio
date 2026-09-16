package com.study.backend.common.interceptor;

import java.time.Instant;
import java.util.Arrays;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicBoolean;

import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.HandlerInterceptor;
import org.springframework.web.servlet.HandlerMapping;
import org.springframework.util.NumberUtils;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.study.backend.common.dto.ApiResponse;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.NonNull;

/**
 * 로그인, 회원가입, 비밀글 비밀번호 검증 요청에
 * IP 기반 슬라이딩 윈도우 Rate Limit을 적용한다.
 */
@Component
public class LoginRateLimitInterceptor implements HandlerInterceptor {

	private static final String TOO_MANY_REQUESTS_MESSAGE = "요청이 너무 많습니다. 잠시 후 다시 시도해주세요.";

	/** 1분 내 허용 최대 로그인 시도 횟수 */
	private static final int LOGIN_MAX_ATTEMPTS = 10;

	/** 1분 내 허용 최대 회원가입 시도 횟수 */
	private static final int SIGN_UP_MAX_ATTEMPTS = 3;

	/** 1분 내 허용 최대 비밀글 비밀번호 검증 횟수 */
	private static final int SECRET_VERIFY_MAX_ATTEMPTS = 5;

	/** 슬라이딩 윈도우 크기 (밀리초 단위, 1분) */
	private static final long WINDOW_MS = 60_000L;

	private static final String LOGIN_PATH = "/api/login";
	private static final String SIGN_UP_PATH = "/api/sign-up";
	private static final String SECRET_VERIFY_PATTERN =
		"/api/{boardType}/{id}/verifySecretPostPassword";

	/** 로그인: IP별 요청 타임스탬프 */
	private final Map<String, long[]> loginAttempts = new ConcurrentHashMap<>();

	/** 회원가입: IP별 요청 타임스탬프 */
	private final Map<String, long[]> signUpAttempts = new ConcurrentHashMap<>();

	/** 비밀글 비밀번호 검증: IP + boardId별 요청 타임스탬프 */
	private final Map<String, long[]> secretVerifyAttempts = new ConcurrentHashMap<>();

	private final ObjectMapper objectMapper;

	public LoginRateLimitInterceptor(ObjectMapper objectMapper) {
		this.objectMapper = objectMapper;
	}

	/** 1분마다 실행: 모든 요청 기록이 만료된 항목을 메모리에서 제거한다. */
	@Scheduled(fixedRate = 60_000)
	public void evictExpiredEntries() {
		long now = Instant.now().toEpochMilli();

		loginAttempts.entrySet().removeIf(entry ->
			Arrays.stream(entry.getValue()).allMatch(timestamp -> now - timestamp >= WINDOW_MS)
		);

		signUpAttempts.entrySet().removeIf(entry ->
			Arrays.stream(entry.getValue()).allMatch(timestamp -> now - timestamp >= WINDOW_MS)
		);

		secretVerifyAttempts.entrySet().removeIf(entry ->
			Arrays.stream(entry.getValue()).allMatch(timestamp -> now - timestamp >= WINDOW_MS)
		);
	}
	/** 로그인, 회원가입, 비밀글 검증 요청에 대해 슬라이딩 윈도우 기반 Rate Limit을 적용한다.*/
	@Override
	public boolean preHandle(@NonNull HttpServletRequest request, @NonNull HttpServletResponse response,
		@NonNull Object handler) throws Exception {
		String ip = resolveClientIp(request);
		long now = Instant.now().toEpochMilli();

		// 로그인 검사
		if(isLoginPath(request)) {
			boolean allowed = tryRecordAttempt(loginAttempts, ip, now, LOGIN_MAX_ATTEMPTS);
			if(!allowed) {
				writeTooManyRequests(response);
				return false;
			}
			return true;
		}

		// 회원가입 검사
		if (isSignUpPath(request)) {
			boolean allowed = tryRecordAttempt(
				signUpAttempts,
				ip,
				now,
				SIGN_UP_MAX_ATTEMPTS
			);

			if (!allowed) {
				writeTooManyRequests(response);
				return false;
			}

			return true;
		}

		// 비밀글 검사
		if (isSecretVerifyPath(request)) {
			String boardId = extractBoardIdFromSecretVerifyPath(request);

			if (boardId == null) {
				return true;

			}
			String attemptKey = buildSecretAttemptKey(ip, boardId);

			boolean allowed =tryRecordAttempt(
				secretVerifyAttempts,
				attemptKey,
				now,
				SECRET_VERIFY_MAX_ATTEMPTS
			);

			if(!allowed) {
				writeTooManyRequests(response);
				return false;
			}
		}

		return true;
	}

	/** 현재 nginx 등 리버스 프록시를 사용하지 않으므로 RemoteAddr로 실제 클라이언트 IP를 직접 가져옴 */
	private String resolveClientIp(HttpServletRequest request) {
		return request.getRemoteAddr();
	}

	/** Spring MVC가 확정한 컨트롤러 매핑을 기준으로 검사한다. */
	private boolean isLoginPath(HttpServletRequest request) {
		return LOGIN_PATH.equals(request.getAttribute(HandlerMapping.BEST_MATCHING_PATTERN_ATTRIBUTE));
	}

	private boolean isSignUpPath(HttpServletRequest request) {
		return SIGN_UP_PATH.equals(request.getAttribute(HandlerMapping.BEST_MATCHING_PATTERN_ATTRIBUTE));
	}

	private boolean isSecretVerifyPath(HttpServletRequest request) {
		return SECRET_VERIFY_PATTERN.equals(
			request.getAttribute(HandlerMapping.BEST_MATCHING_PATTERN_ATTRIBUTE)
		);
	}

	/** Spring MVC가 경로 파라미터를 분리하고 디코딩한 ID를 사용한다. */
	private String extractBoardIdFromSecretVerifyPath(HttpServletRequest request) {
		Object attribute = request.getAttribute(HandlerMapping.URI_TEMPLATE_VARIABLES_ATTRIBUTE);
		if (!(attribute instanceof Map<?, ?> variables)) {
			return null;
		}
		Object id = variables.get("id");
		return id instanceof String rawId ? normalizeBoardId(rawId) : null;
	}

	/** 기본 Spring Long 변환과 같은 방식으로 동일한 ID 표기를 정규화한다. */
	private String normalizeBoardId(String rawBoardId) {
		try {
			long boardId = NumberUtils.parseNumber(rawBoardId, Long.class);
			return boardId > 0 ? Long.toString(boardId) : null;
		} catch (IllegalArgumentException e) {
			return null;
		}
	}

	/** IP와 boardId를 조합하여 Rate Limit 키를 생성한다. */
	private String buildSecretAttemptKey(String ip, String boardId) {
		return ip + ":" + boardId;
	}

	/**
	 * 키별 슬라이딩 윈도우의 시도 횟수를 원자적으로 확인한다.
	 * 제한 미만이면 현재 시각을 기록하고 true를 반환하며,
	 * 제한에 도달하면 기록하지 않고 false를 반환한다.
	 */
	private boolean tryRecordAttempt(Map<String, long[]> attempts, String key, long now, int maxAttempts){
		AtomicBoolean allowed = new AtomicBoolean(false);

		attempts.compute(key, (k, timestamps) -> {
			long[] recent = timestamps == null ? new long[0] : Arrays.stream(timestamps).filter(t -> now - t < WINDOW_MS).toArray();

			if(recent.length >= maxAttempts) {
				return recent;
			}

			long[] updated = Arrays.copyOf(recent, recent.length + 1);
			updated[recent.length] = now;
			allowed.set(true);

			return updated;
		});

		return allowed.get();
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
