package com.study.backend.common.resolver;

import java.util.Arrays;
import java.util.Objects;

import org.springframework.core.MethodParameter;
import org.springframework.stereotype.Component;
import org.springframework.web.bind.support.WebDataBinderFactory;
import org.springframework.web.context.request.NativeWebRequest;
import org.springframework.web.method.support.HandlerMethodArgumentResolver;
import org.springframework.web.method.support.ModelAndViewContainer;

import com.study.backend.common.annotation.LoginMember;
import com.study.backend.common.util.AuthRequestAttributes;
import com.study.backend.common.util.JwtTokenProvider;

import jakarta.servlet.http.Cookie;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;

@Component
@RequiredArgsConstructor
public class LoginMemberArgumentResolver implements HandlerMethodArgumentResolver {

	private final JwtTokenProvider jwtTokenProvider;

	@Override
	public boolean supportsParameter(MethodParameter parameter) {
		return parameter.hasParameterAnnotation(LoginMember.class)
			&& parameter.getParameterType().equals(Long.class);
	}

	/**
	 * 인터셉터에서 이미 인증된 memberId가 있으면 그대로 사용하고,
	 * 없으면 쿠키 토큰에서 추출한다. required 여부에 따라 예외 또는 null을 반환한다.
	 */
	@Override
	public Object resolveArgument(MethodParameter parameter, ModelAndViewContainer mavContainer,
								  NativeWebRequest webRequest, WebDataBinderFactory binderFactory) {
		HttpServletRequest request = (HttpServletRequest) webRequest.getNativeRequest();
		Long resolvedMemberId = (Long) request.getAttribute(AuthRequestAttributes.MEMBER_ID);
		LoginMember annotation = Objects.requireNonNull(
			parameter.getParameterAnnotation(LoginMember.class),
			"@LoginMember annotation is required"
		);

		if (resolvedMemberId != null) {
			return resolvedMemberId;
		}

		String token = extractToken(request);
		if (annotation.required()) {
			return jwtTokenProvider.getMemberId(token);
		}
		return jwtTokenProvider.getOptionalMemberId(token);
	}

	private String extractToken(HttpServletRequest request) {
		if (request.getCookies() == null) return null;
		return Arrays.stream(request.getCookies())
			.filter(c -> "token".equals(c.getName()))
			.map(Cookie::getValue)
			.findFirst()
			.orElse(null);
	}
}
