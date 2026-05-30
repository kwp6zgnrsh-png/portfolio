package com.study.backend.member.api;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.ResponseCookie;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import com.study.backend.common.interceptor.JwtAuthInterceptor;
import com.study.backend.common.interceptor.LoginRateLimitInterceptor;
import com.study.backend.common.util.AuthCookieFactory;
import com.study.backend.common.util.JwtTokenProvider;
import com.study.backend.member.dto.response.LoginResult;
import com.study.backend.member.service.MemberService;

@WebMvcTest(MemberApi.class)
class MemberApiTest {

	@Autowired
	MockMvc mockMvc;

	@MockBean
	MemberService memberService;

	@MockBean
	JwtAuthInterceptor jwtAuthInterceptor;

	@MockBean
	LoginRateLimitInterceptor loginRateLimitInterceptor;

	@MockBean
	JwtTokenProvider jwtTokenProvider;

	@MockBean
	AuthCookieFactory authCookieFactory;

	@Test
	void authenticateMember_blankPassword_returns400() throws Exception {
		given(jwtAuthInterceptor.preHandle(any(), any(), any())).willReturn(true);
		given(loginRateLimitInterceptor.preHandle(any(), any(), any())).willReturn(true);

		mockMvc.perform(post("/api/login")
				.contentType(MediaType.APPLICATION_JSON)
				.content("""
					{
					  "memberId": "user1",
					  "memberPassword": ""
					}
					"""))
			.andExpect(status().isBadRequest())
			.andExpect(jsonPath("$.message").value("비밀번호 입력해 주세요"));
	}

	@Test
	void authenticateMember_validRequest_returns200() throws Exception {
		given(jwtAuthInterceptor.preHandle(any(), any(), any())).willReturn(true);
		given(loginRateLimitInterceptor.preHandle(any(), any(), any())).willReturn(true);
		given(memberService.authenticateMember(any()))
			.willReturn(new LoginResult("jwt-token", 1L, "홍길동"));
		given(authCookieFactory.createLoginTokenCookie("jwt-token"))
			.willReturn(ResponseCookie.from("token", "jwt-token").build());

		mockMvc.perform(post("/api/login")
				.contentType(MediaType.APPLICATION_JSON)
				.content("""
					{
					  "memberId": "user1",
					  "memberPassword": "pass1234"
					}
					"""))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.message").value("로그인 성공"))
			.andExpect(jsonPath("$.payload.memberId").value(1L))
			.andExpect(jsonPath("$.payload.memberName").value("홍길동"));
	}
}
