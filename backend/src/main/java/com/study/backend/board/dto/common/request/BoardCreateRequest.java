package com.study.backend.board.dto.common.request;

import jakarta.validation.constraints.AssertTrue;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record BoardCreateRequest(
	@NotBlank(message = "제목을 작성해주세요.")
	@Size(max = 100, message = "제목은 100자 미만입니다.")
	String title,

	@NotBlank(message = "내용을 작성해주세요.")
	@Size(max = 4000, message = "내용은 4000자 미만입니다.")
	String content,

	Long categoryId,
	Boolean isSecret,
	String secretPassword
) {

	public BoardCreateRequest {
		if (isSecret == null) {
			isSecret = false;
		}
	}

	@AssertTrue(message = "비공개 시 비밀번호는 숫자 4자리가 필요합니다")
	public boolean isSecretPasswordValid() {
		if (Boolean.TRUE.equals(isSecret)) {
			return secretPassword != null && secretPassword.matches("^\\d{4}$");
		}
		return true;
	}
}
