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
    @DisplayName("비밀글 비밀번호 검증 성공(2xx) 시 실패 누적이 초기화된다")
    void afterCompletion_secretVerifySuccess_resetsFailures() throws Exception {
        MockHttpServletRequest secretRequest = new MockHttpServletRequest();
        secretRequest.setRequestURI("/api/inquiries/1/verifySecretPostPassword");
        secretRequest.setRemoteAddr("127.0.0.1");

        for (int i = 0; i < 4; i++) {
            MockHttpServletResponse failedResponse = new MockHttpServletResponse();
            assertThat(interceptor.preHandle(secretRequest, failedResponse, new Object())).isTrue();
            failedResponse.setStatus(HttpStatus.FORBIDDEN.value());
            interceptor.afterCompletion(secretRequest, failedResponse, new Object(), null);
        }

        MockHttpServletResponse successResponse = new MockHttpServletResponse();
        assertThat(interceptor.preHandle(secretRequest, successResponse, new Object())).isTrue();
        successResponse.setStatus(HttpStatus.OK.value());
        interceptor.afterCompletion(secretRequest, successResponse, new Object(), null);

        for (int i = 0; i < 5; i++) {
            MockHttpServletResponse failedResponse = new MockHttpServletResponse();
            assertThat(interceptor.preHandle(secretRequest, failedResponse, new Object())).isTrue();
            failedResponse.setStatus(HttpStatus.FORBIDDEN.value());
            interceptor.afterCompletion(secretRequest, failedResponse, new Object(), null);
        }
    }
}
