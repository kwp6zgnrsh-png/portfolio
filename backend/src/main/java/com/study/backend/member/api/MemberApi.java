package com.study.backend.member.api;

import java.util.Map;

import org.springframework.http.HttpHeaders;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.study.backend.common.annotation.Public;
import com.study.backend.common.dto.ApiResponse;
import com.study.backend.common.util.AuthCookieFactory;
import com.study.backend.member.dto.request.LoginRequest;
import com.study.backend.member.dto.request.MemberIdRequest;
import com.study.backend.member.dto.request.MemberRequest;
import com.study.backend.member.dto.response.LoginResult;
import com.study.backend.member.model.Member;
import com.study.backend.member.service.MemberService;

import jakarta.servlet.http.HttpServletResponse;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;

@Public
@RestController
@RequestMapping("/api")
@RequiredArgsConstructor
public class MemberApi {

	private final MemberService memberService;
	private final AuthCookieFactory authCookieFactory;

	/** 아이디와 비밀번호로 로그인하고 JWT 토큰을 반환한다. */
	@PostMapping("/login")
	public ApiResponse<?> authenticateMember(@RequestBody @Valid LoginRequest loginRequest,
									      HttpServletResponse response){
		LoginResult result = memberService.authenticateMember(loginRequest.toMember());

		response.addHeader(HttpHeaders.SET_COOKIE, authCookieFactory.createLoginTokenCookie(result.token()).toString());
		return ApiResponse.of("로그인 성공", Map.of(
				"memberName", result.memberName(),
				"memberId", result.memberId()));
	}


	/** 로그아웃하고 토큰 쿠키를 만료시킨다. */
	@PostMapping("/logout")
	public ApiResponse<?> logout(HttpServletResponse response) {
		response.addHeader(HttpHeaders.SET_COOKIE, authCookieFactory.expireLoginTokenCookie().toString());
		return ApiResponse.of("로그아웃");
	}


	/** 회원가입 유효성 검사 후 회원을 등록한다. */
	@PostMapping("/sign-up")
	public ApiResponse<?> createMember(@RequestBody @Valid MemberRequest memberRequest){

		Member member = memberRequest.toMember();
		memberService.createMember(member, memberRequest.confirmPassword());

		return ApiResponse.of("회원가입 완료");
	}

	/** 아이디 사용 가능 여부를 검사한다. 금지 목록 포함 또는 중복이면 예외를 던진다. */
	@PostMapping("/validate-id")
	public ApiResponse<?> validateMemberId(@RequestBody @Valid MemberIdRequest memberIdRequest){
		memberService.validateMemberIdAvailable(memberIdRequest.memberId());

		return ApiResponse.of("사용 가능한 아이디입니다.");
	}
}
