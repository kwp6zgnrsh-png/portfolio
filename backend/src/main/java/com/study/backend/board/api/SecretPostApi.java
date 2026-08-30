package com.study.backend.board.api;

import org.springframework.http.HttpHeaders;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.study.backend.board.dto.secret.dto.request.SecretPostPasswordRequest;
import com.study.backend.board.strategy.BoardStrategyFactory;
import com.study.backend.common.annotation.LoginMember;
import com.study.backend.common.annotation.Public;
import com.study.backend.common.dto.ApiResponse;
import com.study.backend.common.util.AuthCookieFactory;
import com.study.backend.common.util.JwtTokenProvider;

import jakarta.servlet.http.HttpServletResponse;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;

@RestController
@RequiredArgsConstructor
@RequestMapping("/api")
public class SecretPostApi {

	private final BoardStrategyFactory boardStrategyFactory;
	private final JwtTokenProvider jwtTokenProvider;
	private final AuthCookieFactory authCookieFactory;

	/** 로그인된 회원이 해당 비밀글의 작성자인지 확인한다. */
	@Public
	@PostMapping("/{boardType}/{id}/verifyMySecretPost")
	public ApiResponse<?> verifyMySecretPost(@PathVariable String boardType,
										     @PathVariable("id") Long boardId,
										     @LoginMember(required = false) Long memberId) {

		boolean isOwner = boardStrategyFactory.requireSecretStrategy(boardType).isPostOwner(boardId, memberId);

		if (!isOwner) {
			return ApiResponse.of("비밀번호입력");
		}

		return ApiResponse.of("본인");
	}

	/** 비밀글의 비밀번호를 검증하고, 성공 시 10분짜리 secret_token 쿠키를 발급한다. */
	@Public
	@PostMapping("/{boardType}/{id}/verifySecretPostPassword")
	public ApiResponse<?> verifySecretPostPassword(@PathVariable String boardType,
												   @PathVariable("id") Long boardId,
											       @RequestBody @Valid SecretPostPasswordRequest requestPassword,
											       HttpServletResponse response) {

		boardStrategyFactory.requireSecretStrategy(boardType)
			.verifySecretPassword(boardId, requestPassword.password());

		String secretToken = jwtTokenProvider.createSecretAccessToken(boardId);
		response.addHeader(HttpHeaders.SET_COOKIE, authCookieFactory.createSecretTokenCookie(secretToken).toString());

		return ApiResponse.of("본인 확인");
	}
}
