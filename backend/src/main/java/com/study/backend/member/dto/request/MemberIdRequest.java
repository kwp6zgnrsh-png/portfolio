package com.study.backend.member.dto.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;

public record MemberIdRequest(
	@NotBlank(message = "아이디를 입력해 주세요")
	@Pattern(regexp = "^[a-z0-9_-]{4,11}$", message = "아이디는 a-z, 0-9, '-', '_'만 허용하며 4자 이상 12자리 미만입니다")
	String memberId
) {
}
