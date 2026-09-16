package com.study.backend.common.interceptor;

import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

import java.net.URI;
import java.util.List;
import java.util.stream.Stream;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.springframework.http.ResponseEntity;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.HandlerInterceptor;
import org.springframework.web.servlet.config.annotation.InterceptorRegistry;
import org.springframework.web.util.pattern.PathPatternParser;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.study.backend.common.config.WebConfig;
import com.study.backend.common.resolver.LoginMemberArgumentResolver;

class LoginRateLimitInterceptorTest {
    private MockMvc mvc;

    @BeforeEach
    void setUp() throws Exception {
        LoginRateLimitInterceptor limiter = new LoginRateLimitInterceptor(new ObjectMapper());
        JwtAuthInterceptor jwt = mock(JwtAuthInterceptor.class);
        when(jwt.preHandle(any(), any(), any())).thenReturn(true);

        // 실제 WebConfig의 인터셉터 등록 설정도 검증한다.
        WebConfig config = new WebConfig(
            jwt, limiter, mock(LoginMemberArgumentResolver.class)
        );
        TestRegistry registry = new TestRegistry();
        config.addInterceptors(registry);

        mvc = MockMvcBuilders.standaloneSetup(new ProbeController())
            .setPatternParser(new PathPatternParser())
            .addInterceptors(registry.interceptors())
            .build();
    }

    static Stream<Arguments> variants() {
        String secret = "/api/inquiries/1/verifySecretPostPassword";
        return Stream.of(
            Arguments.of("/api/login", "/api/login;x=1", 10),
            Arguments.of("/api/login", "/api;x=1/login", 10),
            Arguments.of("/api/login", "/api/%6Cogin", 10),
            Arguments.of("/api/sign-up", "/api/sign-up;x=1", 3),
            Arguments.of("/api/sign-up", "/api;x=1/sign-up", 3),
            Arguments.of("/api/sign-up", "/api/%73ign-up", 3),
            Arguments.of(secret, "/api/inquiries/1;x=1/verifySecretPostPassword", 5),
            Arguments.of(secret, "/api/inquiries/1/verifySecretPostPassword;x=1", 5),
            Arguments.of(secret, "/api;x=1/inquiries;y=2/1;z=3/verifySecretPostPassword;a=4", 5),
            Arguments.of(secret, "/api/inquiries/001/verifySecretPostPassword", 5),
            Arguments.of(secret, "/api/inquiries/%31/verifySecretPostPassword", 5),
            Arguments.of(secret, "/api/inquiries/0x1/verifySecretPostPassword", 5)
        );
    }

    @ParameterizedTest
    @MethodSource("variants")
    @DisplayName("정상 경로의 요청 한도를 소진하면 변형 경로도 차단한다")
    void normalThenVariant_sharesLimit(String normal, String variant, int limit) throws Exception {
        for (int i = 0; i < limit; i++) {
            send(normal, "127.0.0.1").andExpect(status().isOk());
        }
        send(variant, "127.0.0.1")
            .andExpect(status().isTooManyRequests())
            .andExpect(jsonPath("$.message").value("요청이 너무 많습니다. 잠시 후 다시 시도해주세요."));
    }

    @ParameterizedTest
    @MethodSource("variants")
    @DisplayName("변형 경로의 요청 한도를 소진하면 정상 경로도 차단한다")
    void variantThenNormal_sharesLimit(String normal, String variant, int limit) throws Exception {
        // 우회 경로가 실제 컨트롤러에 도달하는지도 200 응답으로 확인한다.
        for (int i = 0; i < limit; i++) {
            send(variant, "127.0.0.1").andExpect(status().isOk());
        }
        send(normal, "127.0.0.1").andExpect(status().isTooManyRequests());
    }

    @Test
    @DisplayName("IP별로 독립 집계하며 X-Forwarded-For로 제한을 우회할 수 없다")
    void differentIps_areIndependent_andForwardedForCannotResetLimit() throws Exception {
        for (int i = 0; i < 10; i++) {
            send("/api/login", "127.0.0.1").andExpect(status().isOk());
        }
        send("/api/login", "192.168.0.1").andExpect(status().isOk());
        mvc.perform(post("/api/login")
                .header("X-Forwarded-For", "10.0.0.99")
                .with(request -> { request.setRemoteAddr("127.0.0.1"); return request; }))
            .andExpect(status().isTooManyRequests());
    }

    @Test
    @DisplayName("비밀글 검증 횟수는 게시글별로 독립 집계한다")
    void differentBoards_areIndependent() throws Exception {
        for (int i = 0; i < 5; i++) {
            send("/api/inquiries/1/verifySecretPostPassword", "127.0.0.1")
                .andExpect(status().isOk());
        }
        send("/api/inquiries/2/verifySecretPostPassword", "127.0.0.1")
            .andExpect(status().isOk());
        send("/api/inquiries/1/verifySecretPostPassword", "127.0.0.1")
            .andExpect(status().isTooManyRequests());
    }

    @Test
    @DisplayName("요청이 성공해도 이전 실패 횟수는 초기화되지 않는다")
    void successfulRequest_doesNotResetFailedAttempts() throws Exception {
        for (String path : List.of("/api/login", "/api/inquiries/1/verifySecretPostPassword")) {
            int limit = path.equals("/api/login") ? 10 : 5;
            for (int i = 0; i < limit - 1; i++) {
                send(path + "?fail=true", "127.0.0.1").andExpect(status().isForbidden());
            }
            send(path, "127.0.0.1").andExpect(status().isOk());
            send(path, "127.0.0.1").andExpect(status().isTooManyRequests());
        }
    }

    @Test
    @DisplayName("제한 대상이 아닌 API는 한도를 초과해도 통과한다")
    void unrelatedEndpoint_isNotLimited() throws Exception {
        for (int i = 0; i < 15; i++) {
            send("/api/other", "127.0.0.1").andExpect(status().isOk());
        }
    }

    private ResultActions send(String path, String ip) throws Exception {
        // URI 오버로드로 퍼센트 문자의 이중 인코딩을 방지한다.
        return mvc.perform(post(URI.create(path)).with(request -> {
            request.setRemoteAddr(ip);
            return request;
        }));
    }

    private static class TestRegistry extends InterceptorRegistry {
        HandlerInterceptor[] interceptors() {
            return getInterceptors().toArray(HandlerInterceptor[]::new);
        }
    }

    @RestController
    @RequestMapping("/api")
    static class ProbeController {
        @PostMapping({"/login", "/sign-up", "/other"})
        ResponseEntity<String> simple(@RequestParam(name = "fail", defaultValue = "false") boolean fail) {
            return ResponseEntity.status(fail ? 403 : 200).body("test");
        }

        @PostMapping("/{boardType}/{id}/verifySecretPostPassword")
        ResponseEntity<String> secret(@PathVariable("boardType") String boardType, @PathVariable("id") Long id,
                                      @RequestParam(name = "fail", defaultValue = "false") boolean fail) {
            return ResponseEntity.status(fail ? 403 : 200).body(id.toString());
        }
    }
}
