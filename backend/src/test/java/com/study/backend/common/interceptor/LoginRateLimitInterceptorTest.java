package com.study.backend.common.interceptor;

import static org.assertj.core.api.Assertions.*;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

import com.fasterxml.jackson.databind.ObjectMapper;

class LoginRateLimitInterceptorTest {

    private LoginRateLimitInterceptor interceptor;
    private MockHttpServletRequest request;
    private MockHttpServletResponse response;

    @BeforeEach
    void setUp() {
        interceptor = new LoginRateLimitInterceptor(new ObjectMapper());
        request = new MockHttpServletRequest();
        request.setRequestURI("/api/login");
        request.setRemoteAddr("127.0.0.1");
        response = new MockHttpServletResponse();
    }

    @Test
    @DisplayName("허용 횟수 이하의 요청은 통과한다")
    void preHandle_underLimit_returnsTrue() throws Exception {
        for (int i = 0; i < 10; i++) {
            boolean result = interceptor.preHandle(request, response, new Object());
            assertThat(result).isTrue();
        }
    }

    @Test
    @DisplayName("허용 횟수 초과 시 429를 반환하고 false를 반환한다")
    void preHandle_overLimit_returns429() throws Exception {
        for (int i = 0; i < 10; i++) {
            interceptor.preHandle(request, response, new Object());
        }

        boolean result = interceptor.preHandle(request, response, new Object());

        assertThat(result).isFalse();
        assertThat(response.getStatus()).isEqualTo(HttpStatus.TOO_MANY_REQUESTS.value());
        assertThat(response.getContentAsString()).contains("\"message\":\"요청이 너무 많습니다. 잠시 후 다시 시도해주세요.\"");
    }

	@Test
	@DisplayName("로그인 성공으로 같은 IP의 이전 시도 횟수를 초기화할 수 없다")
	void afterCompletion_loginSuccess_doesNotResetIpAttempts() throws Exception {
		for (int i = 0; i < 9; i++) {
			MockHttpServletResponse failedResponse = new MockHttpServletResponse();
			assertThat(interceptor.preHandle(request, failedResponse, new Object())).isTrue();
			failedResponse.setStatus(HttpStatus.BAD_REQUEST.value());
			interceptor.afterCompletion(request, failedResponse, new Object(), null);
		}

		MockHttpServletResponse successResponse = new MockHttpServletResponse();
		assertThat(interceptor.preHandle(request, successResponse, new Object())).isTrue();
		successResponse.setStatus(HttpStatus.OK.value());
		interceptor.afterCompletion(request, successResponse, new Object(), null);

		MockHttpServletResponse nextResponse = new MockHttpServletResponse();
		assertThat(interceptor.preHandle(request, nextResponse, new Object())).isFalse();
		assertThat(nextResponse.getStatus()).isEqualTo(HttpStatus.TOO_MANY_REQUESTS.value());
	}

    @Test
    @DisplayName("다른 IP는 독립적으로 카운팅된다")
    void preHandle_differentIps_countedSeparately() throws Exception {
        for (int i = 0; i < 10; i++) {
            interceptor.preHandle(request, response, new Object());
        }

        MockHttpServletRequest otherRequest = new MockHttpServletRequest();
        otherRequest.setRequestURI("/api/login");
        otherRequest.setRemoteAddr("192.168.0.1");
        MockHttpServletResponse otherResponse = new MockHttpServletResponse();

        boolean result = interceptor.preHandle(otherRequest, otherResponse, new Object());

        assertThat(result).isTrue();
        assertThat(otherResponse.getStatus()).isNotEqualTo(HttpStatus.TOO_MANY_REQUESTS.value());
    }

    @Test
    @DisplayName("X-Forwarded-For 헤더가 있어도 RemoteAddr로 카운팅한다")
    void preHandle_forwardedFor_usesRemoteAddr() throws Exception {
        request.addHeader("X-Forwarded-For", "10.0.0.1, 10.0.0.2");

        for (int i = 0; i < 10; i++) {
            interceptor.preHandle(request, response, new Object());
        }

        boolean result = interceptor.preHandle(request, response, new Object());

        assertThat(result).isFalse();
        assertThat(response.getStatus()).isEqualTo(HttpStatus.TOO_MANY_REQUESTS.value());
    }

    @Test
    @DisplayName("비밀글 비밀번호 검증은 실패(403) 누적 5회 이후 429를 반환한다")
    void preHandle_secretVerifyFailedTooManyTimes_returns429() throws Exception {
        MockHttpServletRequest secretRequest = new MockHttpServletRequest();
        secretRequest.setRequestURI("/api/inquiries/1/verifySecretPostPassword");
        secretRequest.setRemoteAddr("127.0.0.1");
        MockHttpServletResponse secretResponse = new MockHttpServletResponse();

        for (int i = 0; i < 5; i++) {
            boolean allowed = interceptor.preHandle(secretRequest, secretResponse, new Object());
            assertThat(allowed).isTrue();
            secretResponse.setStatus(HttpStatus.FORBIDDEN.value());
            interceptor.afterCompletion(secretRequest, secretResponse, new Object(), null);
            secretResponse = new MockHttpServletResponse();
        }

        boolean blocked = interceptor.preHandle(secretRequest, secretResponse, new Object());

        assertThat(blocked).isFalse();
        assertThat(secretResponse.getStatus()).isEqualTo(HttpStatus.TOO_MANY_REQUESTS.value());
    }

    @Test
    @DisplayName("비밀글 검증 성공 후에도 요청 횟수는 초기화되지 않는다")
    void afterCompletion_secretVerifySuccess_doesNotResetAttempts()
        throws Exception {

        MockHttpServletRequest secretRequest = new MockHttpServletRequest();

        secretRequest.setRequestURI("/api/inquiries/1/verifySecretPostPassword");
        secretRequest.setRemoteAddr("127.0.0.1");

        // 앞선 요청 4회
        for (int i = 0; i < 4; i++) {
            MockHttpServletResponse failedResponse = new MockHttpServletResponse();

            assertThat(
                interceptor.preHandle(
                    secretRequest,
                    failedResponse,
                    new Object()
                )
            ).isTrue();
        }

        // 성공한 요청도 5번째 요청으로 누적
        MockHttpServletResponse successResponse = new MockHttpServletResponse();

        assertThat(
            interceptor.preHandle(
                secretRequest,
                successResponse,
                new Object()
            )
        ).isTrue();

        successResponse.setStatus(HttpStatus.OK.value());

        interceptor.afterCompletion(
            secretRequest,
            successResponse,
            new Object(),
            null
        );

        // 성공했어도 초기화되지 않으므로 6번째 요청은 차단
        MockHttpServletResponse blockedResponse = new MockHttpServletResponse();

        assertThat(
            interceptor.preHandle(
                secretRequest,
                blockedResponse,
                new Object()
            )
        ).isFalse();

        assertThat(blockedResponse.getStatus()).isEqualTo(HttpStatus.TOO_MANY_REQUESTS.value());
    }

    @Test
    @DisplayName("같은 게시글 ID의 선행 0 표기는 동일한 제한 키를 사용한다")
    void equivalentBoardIdsShareAttempts() throws Exception {
        String[] paths = {
            "/api/inquiries/1/verifySecretPostPassword",
            "/api/inquiries/01/verifySecretPostPassword",
            "/api/inquiries/001/verifySecretPostPassword",
            "/api/inquiries/0001/verifySecretPostPassword",
            "/api/inquiries/1/verifySecretPostPassword"
        };

        for (String path : paths) {
            MockHttpServletRequest request = new MockHttpServletRequest("POST", path);
            request.setRemoteAddr("127.0.0.1");

            assertThat(interceptor.preHandle(request, response, new Object())).isTrue();
        }

        MockHttpServletRequest blockedRequest = new MockHttpServletRequest(
                "POST",
                "/api/inquiries/01/verifySecretPostPassword"
        );
        blockedRequest.setRemoteAddr("127.0.0.1");

        assertThat(interceptor.preHandle(blockedRequest, response, new Object())).isFalse();

        assertThat(response.getStatus()).isEqualTo(429);
    }

    @Test
    @DisplayName("회원가입 요청은 IP당 5회 이후 429를 반환한다")
    void preHandle_signUpOverLimit_returns429() throws Exception {
        MockHttpServletRequest signUpRequest =
            new MockHttpServletRequest("POST", "/api/sign-up");
        signUpRequest.setRemoteAddr("127.0.0.1");

        for (int i = 0; i < 3; i++) {
            MockHttpServletResponse allowedResponse = new MockHttpServletResponse();
            assertThat(interceptor.preHandle(signUpRequest, allowedResponse, new Object())
            ).isTrue();
        }

        MockHttpServletResponse blockedResponse = new MockHttpServletResponse();

        assertThat(interceptor.preHandle(signUpRequest, blockedResponse, new Object())).isFalse();

        assertThat(blockedResponse.getStatus()).isEqualTo(HttpStatus.TOO_MANY_REQUESTS.value());
    }
}
