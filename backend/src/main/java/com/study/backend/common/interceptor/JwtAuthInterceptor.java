package com.study.backend.common.interceptor;

import java.util.Arrays;

import org.springframework.stereotype.Component;
import org.springframework.web.method.HandlerMethod;
import org.springframework.web.servlet.HandlerInterceptor;

import com.study.backend.common.annotation.Public;
import com.study.backend.common.exception.AuthorizationException;
import com.study.backend.common.util.AuthRequestAttributes;
import com.study.backend.common.util.JwtTokenProvider;

import jakarta.servlet.http.Cookie;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.NonNull;
import lombok.RequiredArgsConstructor;

@Component
@RequiredArgsConstructor
public class JwtAuthInterceptor implements HandlerInterceptor {

	private final JwtTokenProvider jwtTokenProvider;

	/** @Public이 아닌 엔드포인트는 token 쿠키의 JWT를 검증하고 memberId를 request에 저장한다. */
	@Override
	public boolean preHandle(@NonNull HttpServletRequest request, @NonNull HttpServletResponse response, @NonNull Object handler) {
		if (!(handler instanceof HandlerMethod handlerMethod)) {
			return true;
		}

		boolean isPublic = handlerMethod.hasMethodAnnotation(Public.class)
			|| handlerMethod.getBeanType().isAnnotationPresent(Public.class);

		if (isPublic) {
			return true;
		}

		String token = extractToken(request);
		if (token == null || token.isBlank()) {
			throw new AuthorizationException("인증이 필요합니다");
		}

		Long memberId = jwtTokenProvider.getMemberId(token);
		if (memberId == null) {
			throw new AuthorizationException("인증 정보가 올바르지 않습니다");
		}
		request.setAttribute(AuthRequestAttributes.MEMBER_ID, memberId);
		return true;
	}

	private String extractToken(HttpServletRequest request) {
		if (request.getCookies() == null) {
			return null;
		}
		return Arrays.stream(request.getCookies())
			.filter(c -> "token".equals(c.getName()))
			.map(Cookie::getValue)
			.findFirst()
			.orElse(null);
	}
}
