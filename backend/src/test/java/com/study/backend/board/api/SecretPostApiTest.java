package com.study.backend.board.api;

import static org.mockito.BDDMockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import com.study.backend.board.exception.BoardNotFoundException;
import com.study.backend.board.exception.BoardPermissionDeniedException;
import com.study.backend.board.strategy.BoardSecretStrategy;
import com.study.backend.board.strategy.BoardStrategyFactory;
import com.study.backend.common.interceptor.JwtAuthInterceptor;
import com.study.backend.common.interceptor.LoginRateLimitInterceptor;
import com.study.backend.common.util.AuthCookieFactory;
import com.study.backend.common.util.JwtTokenProvider;

import jakarta.servlet.http.Cookie;

@WebMvcTest(SecretPostApi.class)
class SecretPostApiTest {

	private static final Cookie AUTH_COOKIE = new Cookie("token", "valid-token");

	@Autowired MockMvc mockMvc;

	@MockBean BoardStrategyFactory boardStrategyFactory;
	@MockBean JwtTokenProvider jwtTokenProvider;
	@MockBean AuthCookieFactory authCookieFactory;
	@MockBean JwtAuthInterceptor jwtAuthInterceptor;
	@MockBean LoginRateLimitInterceptor loginRateLimitInterceptor;
	@MockBean BoardSecretStrategy secretBoardStrategy;

	private void allowInterceptors() throws Exception {
		given(jwtAuthInterceptor.preHandle(any(), any(), any())).willReturn(true);
		given(loginRateLimitInterceptor.preHandle(any(), any(), any())).willReturn(true);
	}

	private void givenOptionalAuthenticatedMember(Long memberId) {
		given(jwtTokenProvider.getOptionalMemberId(any())).willReturn(memberId);
	}

	@Test
	@DisplayName("비밀글 작성자 확인 시 게시글이 없으면 404를 반환한다")
	void verifyMySecretPost_notFound_returns404() throws Exception {
		allowInterceptors();
		given(boardStrategyFactory.requireSecretStrategy("inquiries")).willReturn(secretBoardStrategy);
		given(secretBoardStrategy.isPostOwner(anyLong(), any())).willThrow(new BoardNotFoundException("삭제된 게시글입니다."));

		mockMvc.perform(post("/api/inquiries/1/verifyMySecretPost"))
			.andExpect(status().isNotFound());
	}

	@Test
	@DisplayName("비밀글 작성자 본인이면 '본인' 메시지를 반환한다")
	void verifyMySecretPost_owner_returnsOwnerMessage() throws Exception {
		allowInterceptors();
		given(boardStrategyFactory.requireSecretStrategy("inquiries")).willReturn(secretBoardStrategy);
		givenOptionalAuthenticatedMember(1L);
		given(secretBoardStrategy.isPostOwner(1L, 1L)).willReturn(true);

		mockMvc.perform(post("/api/inquiries/1/verifyMySecretPost").cookie(AUTH_COOKIE))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.message").value("본인"));
	}

	@Test
	@DisplayName("비밀글 작성자가 아니면 '비밀번호입력' 메시지를 반환한다")
	void verifyMySecretPost_notOwner_returnsPasswordInputMessage() throws Exception {
		allowInterceptors();
		given(boardStrategyFactory.requireSecretStrategy("inquiries")).willReturn(secretBoardStrategy);
		givenOptionalAuthenticatedMember(1L);
		given(secretBoardStrategy.isPostOwner(1L, 1L)).willReturn(false);

		mockMvc.perform(post("/api/inquiries/1/verifyMySecretPost").cookie(AUTH_COOKIE))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.message").value("비밀번호입력"));
	}

	@Test
	@DisplayName("비밀번호가 일치하면 200을 반환한다")
	void verifySecretPostPassword_correct_returns200() throws Exception {
		allowInterceptors();
		given(boardStrategyFactory.requireSecretStrategy("inquiries")).willReturn(secretBoardStrategy);
		willDoNothing().given(secretBoardStrategy).verifySecretPassword(1L, "1234");
		given(jwtTokenProvider.createSecretAccessToken(1L)).willReturn("secret-jwt");
		given(authCookieFactory.createSecretTokenCookie("secret-jwt")).willReturn(
			org.springframework.http.ResponseCookie.from("secret_token", "secret-jwt").build());

		mockMvc.perform(post("/api/inquiries/1/verifySecretPostPassword")
				.contentType(MediaType.APPLICATION_JSON)
				.content("{\"password\":\"1234\"}"))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.message").value("본인 확인"));
	}

	@Test
	@DisplayName("비밀번호가 틀리면 403을 반환한다")
	void verifySecretPostPassword_wrong_returns403() throws Exception {
		allowInterceptors();
		given(boardStrategyFactory.requireSecretStrategy("inquiries")).willReturn(secretBoardStrategy);
		willThrow(new BoardPermissionDeniedException("비밀번호가 일치하지 않습니다."))
			.given(secretBoardStrategy).verifySecretPassword(1L, "0000");

		mockMvc.perform(post("/api/inquiries/1/verifySecretPostPassword")
				.contentType(MediaType.APPLICATION_JSON)
				.content("{\"password\":\"0000\"}"))
			.andExpect(status().isForbidden());
	}

	@Test
	@DisplayName("비밀글 비밀번호가 숫자 4자리가 아니면 400을 반환한다")
	void verifySecretPostPassword_invalidFormat_returns400() throws Exception {
		allowInterceptors();

		mockMvc.perform(post("/api/inquiries/1/verifySecretPostPassword")
				.contentType(MediaType.APPLICATION_JSON)
				.content("{\"password\":\"12ab\"}"))
			.andExpect(status().isBadRequest())
			.andExpect(jsonPath("$.message").value("비공개 시 비밀번호는 숫자 4자리가 필요합니다"));
	}
}
