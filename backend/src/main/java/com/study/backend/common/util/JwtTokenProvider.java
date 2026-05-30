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
			.claim("memberId", member.getId())
			.claim("memberName", member.getMemberName())
			.signWith(cachedKey)
			.compact();
	}

	/** token 쿠키에서 memberId를 추출한다. 만료·변조 시 AuthorizationException을 던진다. 토큰이 없으면 null을 반환한다. */
	public Long getMemberId(String token) {
		Claims claims = parseJwtToken(token);
		if (claims == null) return null;
		return claims.get("memberId", Long.class);
	}

	/** token 쿠키에서 memberId를 추출한다. 토큰이 없거나 유효하지 않으면 null을 반환한다. 토큰이 선택적인 @Public 엔드포인트용. */
	public Long getOptionalMemberId(String token) {
		try {
			Claims claims = parseJwtToken(token);
			if (claims == null) return null;
			return claims.get("memberId", Long.class);
		} catch (AuthorizationException e) {
			return null;
		}
	}

	private static final long SECRET_TOKEN_EXP_MS = 10 * 60 * 1000L;

	/** boardId를 담은 10분짜리 비밀글 접근 토큰을 생성한다. */
	public String createSecretAccessToken(Long boardId) {
		return Jwts.builder()
			.header().type("JWT").and()
			.issuedAt(new Date())
			.expiration(new Date(System.currentTimeMillis() + SECRET_TOKEN_EXP_MS))
			.claim("secretBoardId", boardId)
			.signWith(cachedKey)
			.compact();
	}

	/** 비밀글 접근 토큰에서 boardId를 추출한다. 유효하지 않으면 null을 반환한다. */
	public Long getSecretBoardId(String token) {
		try {
			Claims claims = parseJwtToken(token);
			if (claims == null) return null;
			return claims.get("secretBoardId", Long.class);
		} catch (AuthorizationException e) {
			return null;
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
