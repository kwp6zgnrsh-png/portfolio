package com.study.backend.member.dto.response;

public record LoginResult(
	String token,
	Long memberId,
	String memberName
) {
}
