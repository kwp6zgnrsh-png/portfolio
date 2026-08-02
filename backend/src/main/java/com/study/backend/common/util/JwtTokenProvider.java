package com.study.backend.common.util;

import java.util.Date;

import javax.crypto.SecretKey;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import com.study.backend.common.exception.AuthorizationException;
import com.study.backend.member.model.Member;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.ExpiredJwtException;
import io.jsonwebtoken.JwtException;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.io.Decoders;
import io.jsonwebtoken.security.Keys;
import jakarta.annotation.PostConstruct;

@Component
public class JwtTokenProvider {
	private static final String TOKEN_PURPOSE_CLAIM = "tokenPurpose";
	private static final String LOGIN_TOKEN_PURPOSE = "LOGIN";
	private static final String SECRET_TOKEN_PURPOSE = "SECRET";
	private static final long SECRET_TOKEN_EXP_MS = 10 * 60 * 1000L;

	@Value("${jwt.secret}")
	private String secretKey;

	@Value("${jwt.exp}")
	private String exp;

	private SecretKey cachedKey;

	@PostConstruct
	private void init() {
		cachedKey = Keys.hmacShaKeyFor(Decoders.BASE64.decode(secretKey));
	}

	/** 회원 정보를 담은 JWT 액세스 토큰을 생성한다. */
	public String createToken(Member member) {
		return Jwts.builder()
			.header()
			.type("JWT")
			.and()
			.issuedAt(new Date())
			.expiration(new Date(System.currentTimeMillis() + Long.parseLong(exp) * 1000))
			.claim(TOKEN_PURPOSE_CLAIM, LOGIN_TOKEN_PURPOSE)
			.claim("memberId", member.getId())
			.claim("memberName", member.getMemberName())
			.signWith(cachedKey)
			.compact();
	}

	/** 로그인 토큰에서 memberId를 추출한다. 토큰 용도·필수 claim이 올바르지 않으면 예외를 던진다. */
	public Long getMemberId(String token) {
		Claims claims = requireClaims(token);
		validateTokenPurpose(claims, LOGIN_TOKEN_PURPOSE);
		return getRequiredLongClaim(claims, "memberId");
	}

	/** token 쿠키에서 memberId를 추출한다. 토큰이 없거나 유효하지 않으면 null을 반환한다. 토큰이 선택적인 @Public 엔드포인트용. */
	public Long getOptionalMemberId(String token) {
		try {
			return getMemberId(token);
		} catch (AuthorizationException e) {
			return null;
		}
	}

	/** boardId를 담은 10분짜리 비밀글 접근 토큰을 생성한다. */
	public String createSecretAccessToken(Long boardId) {
		return Jwts.builder()
			.header().type("JWT").and()
			.issuedAt(new Date())
			.expiration(new Date(System.currentTimeMillis() + SECRET_TOKEN_EXP_MS))
			.claim(TOKEN_PURPOSE_CLAIM, SECRET_TOKEN_PURPOSE)
			.claim("secretBoardId", boardId)
			.signWith(cachedKey)
			.compact();
	}

	/** 비밀글 접근 토큰에서 boardId를 추출한다. 유효하지 않으면 null을 반환한다. */
	public Long getSecretBoardId(String token) {
		try {
			Claims claims = requireClaims(token);
			validateTokenPurpose(claims, SECRET_TOKEN_PURPOSE);
			return getRequiredLongClaim(claims, "secretBoardId");
		} catch (AuthorizationException e) {
			return null;
		}
	}

	/** 필수 인증에 사용할 토큰이 없으면 예외를 던지고, 있으면 파싱한 claim을 반환한다. */
	private Claims requireClaims(String token) {
		Claims claims = parseJwtToken(token);
		if (claims == null) {
			throw new AuthorizationException("인증이 필요합니다");
		}
		return claims;
	}

	/** 토큰의 용도가 기대한 로그인·비밀글 용도와 정확히 일치하는지 확인한다. */
	private void validateTokenPurpose(Claims claims, String expectedPurpose) {
		Object purpose = claims.get(TOKEN_PURPOSE_CLAIM);
		if (!(purpose instanceof String actualPurpose) || !expectedPurpose.equals(actualPurpose)) {
			throw new AuthorizationException("토큰 용도 오류");
		}
	}

	/** 필수 Long claim이 존재하고 올바른 타입인지 확인한다. */
	private Long getRequiredLongClaim(Claims claims, String claimName) {
		try {
			Long value = claims.get(claimName, Long.class);
			if (value == null) {
				throw new AuthorizationException("토큰 정보 오류");
			}
			return value;
		} catch (JwtException | IllegalArgumentException e) {
			throw new AuthorizationException("토큰 정보 오류");
		}
	}

	/** JWT를 파싱해 클레임을 반환한다. 토큰이 없으면 null, 위·변조·만료 시 예외를 던진다. */
	private Claims parseJwtToken(String token) {
		if (token == null || token.isBlank()) {
			return null;
		}

		try {
			return Jwts.parser()
				.verifyWith(cachedKey)
				.build()
				.parseSignedClaims(token)
				.getPayload();

		} catch (ExpiredJwtException e) {
			throw new AuthorizationException("토큰 만료");
		} catch (SecurityException e) {
			throw new AuthorizationException("토큰 변조");
		} catch (JwtException | IllegalArgumentException e) {
			throw new AuthorizationException("토큰 에러");
		}
	}
}
