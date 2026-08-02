package com.study.backend.common.interceptor;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.BDDMockito.*;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.web.method.HandlerMethod;

import com.study.backend.common.exception.AuthorizationException;
import com.study.backend.common.util.JwtTokenProvider;

import jakarta.servlet.http.Cookie;

@ExtendWith(MockitoExtension.class)
class JwtAuthInterceptorTest {

	@Mock JwtTokenProvider jwtTokenProvider;
	@InjectMocks JwtAuthInterceptor interceptor;

	@Test
	@DisplayName("서명 검증 후 회원 ID가 없으면 보호 API 인증을 거부한다")
	void preHandle_nullMemberId_throwsAuthorizationException() throws Exception {
		MockHttpServletRequest request = new MockHttpServletRequest();
		request.setCookies(new Cookie("token", "signed-token-without-member"));
		MockHttpServletResponse response = new MockHttpServletResponse();
		HandlerMethod handler = new HandlerMethod(
			new ProtectedHandler(),
			ProtectedHandler.class.getMethod("protectedEndpoint")
		);
		given(jwtTokenProvider.getMemberId("signed-token-without-member")).willReturn(null);

		assertThatThrownBy(() -> interceptor.preHandle(request, response, handler))
			.isInstanceOf(AuthorizationException.class)
			.hasMessage("인증 정보가 올바르지 않습니다");
	}

	static class ProtectedHandler {
		public void protectedEndpoint() {
		}
	}
}
