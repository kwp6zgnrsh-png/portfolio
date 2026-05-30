package com.study.backend.member.dto.request;

import com.study.backend.member.model.Member;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

public record MemberRequest(
	@NotBlank(message = "아이디를 입력해 주세요")
	@Pattern(regexp = "^[a-z0-9_-]{4,11}$", message = "아이디는 a-z, 0-9, '-', '_'만 허용하며 4자 이상 12자리 미만입니다")
	String memberId,

	@NotBlank(message = "비밀번호 입력해 주세요")
	@Size(min = 4, max = 11, message = "비밀번호는 4자 이상 12자리 미만입니다")
	String memberPassword,

	@NotBlank(message = "비밀번호 확인을 입력해 주세요")
	String confirmPassword,

	@NotBlank(message = "이름을 입력해 주세요")
	@Size(min = 2, max = 4, message = "이름은 2자 이상 4자 이하입니다")
	String memberName
) {
	public Member toMember() {
		return Member.builder()
			.memberId(memberId)
			.memberPassword(memberPassword)
			.memberName(memberName)
			.build();
	}
}
