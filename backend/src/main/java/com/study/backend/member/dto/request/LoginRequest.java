package com.study.backend.member.dto.request;

import com.study.backend.member.model.Member;

import jakarta.validation.constraints.NotBlank;

public record LoginRequest(
	@NotBlank(message = "아이디를 입력해 주세요")
	String memberId,

	@NotBlank(message = "비밀번호 입력해 주세요")
	String memberPassword
) {
	public Member toMember() {
		return Member.builder()
			.memberId(memberId)
			.memberPassword(memberPassword)
			.build();
	}
}
