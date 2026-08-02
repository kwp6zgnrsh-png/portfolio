package com.study.backend.common.util;

import static org.assertj.core.api.Assertions.*;

import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.Date;

import javax.crypto.SecretKey;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import com.study.backend.common.exception.AuthorizationException;
import com.study.backend.member.model.Member;

import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;

class JwtTokenProviderTest {

	private static final byte[] KEY_BYTES = "0123456789abcdef0123456789abcdef"
		.getBytes(StandardCharsets.UTF_8);

	private JwtTokenProvider tokenProvider;
	private SecretKey signingKey;

	@BeforeEach
	void setUp() {
		tokenProvider = new JwtTokenProvider();
		ReflectionTestUtils.setField(tokenProvider, "secretKey", Base64.getEncoder().encodeToString(KEY_BYTES));
		ReflectionTestUtils.setField(tokenProvider, "exp", "3600");
		ReflectionTestUtils.invokeMethod(tokenProvider, "init");
		signingKey = Keys.hmacShaKeyFor(KEY_BYTES);
	}

	@Test
	@DisplayName("로그인 토큰에서 회원 ID를 추출한다")
	void getMemberId_loginToken_returnsMemberId() {
		String loginToken = tokenProvider.createToken(Member.builder().id(1L).memberName("회원").build());

		assertThat(tokenProvider.getMemberId(loginToken)).isEqualTo(1L);
	}

	@Test
	@DisplayName("비밀글 토큰은 로그인 토큰으로 사용할 수 없다")
	void getMemberId_secretToken_throwsAuthorizationException() {
		String secretToken = tokenProvider.createSecretAccessToken(10L);

		assertThatThrownBy(() -> tokenProvider.getMemberId(secretToken))
			.isInstanceOf(AuthorizationException.class)
			.hasMessage("토큰 용도 오류");
	}

	@Test
	@DisplayName("로그인 토큰은 비밀글 접근 토큰으로 사용할 수 없다")
	void getSecretBoardId_loginToken_returnsNull() {
		String loginToken = tokenProvider.createToken(Member.builder().id(1L).memberName("회원").build());

		assertThat(tokenProvider.getSecretBoardId(loginToken)).isNull();
	}

	@Test
	@DisplayName("비밀글 토큰에서 게시글 ID를 추출한다")
	void getSecretBoardId_secretToken_returnsBoardId() {
		String secretToken = tokenProvider.createSecretAccessToken(10L);

		assertThat(tokenProvider.getSecretBoardId(secretToken)).isEqualTo(10L);
	}

	@Test
	@DisplayName("용도 claim이 없는 기존 토큰은 인증에 사용할 수 없다")
	void getMemberId_tokenWithoutPurpose_throwsAuthorizationException() {
		String tokenWithoutPurpose = Jwts.builder()
			.issuedAt(new Date())
			.expiration(new Date(System.currentTimeMillis() + 60_000))
			.claim("memberId", 1L)
			.signWith(signingKey)
			.compact();

		assertThatThrownBy(() -> tokenProvider.getMemberId(tokenWithoutPurpose))
			.isInstanceOf(AuthorizationException.class)
			.hasMessage("토큰 용도 오류");
	}

	@Test
	@DisplayName("회원 ID가 없는 로그인 토큰은 인증에 사용할 수 없다")
	void getMemberId_loginTokenWithoutMemberId_throwsAuthorizationException() {
		String tokenWithoutMemberId = Jwts.builder()
			.issuedAt(new Date())
			.expiration(new Date(System.currentTimeMillis() + 60_000))
			.claim("tokenPurpose", "LOGIN")
			.signWith(signingKey)
			.compact();

		assertThatThrownBy(() -> tokenProvider.getMemberId(tokenWithoutMemberId))
			.isInstanceOf(AuthorizationException.class)
			.hasMessage("토큰 정보 오류");
	}

	@Test
	@DisplayName("선택 인증에서는 비밀글 토큰을 익명 사용자로 처리한다")
	void getOptionalMemberId_secretToken_returnsNull() {
		String secretToken = tokenProvider.createSecretAccessToken(10L);

		assertThat(tokenProvider.getOptionalMemberId(secretToken)).isNull();
	}
}
