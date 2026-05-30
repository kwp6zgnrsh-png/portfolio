package com.study.backend.board.dto.secret.dto.request;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;

public record SecretPostPasswordRequest(
	@NotNull(message = "비공개 시 비밀번호는 숫자 4자리가 필요합니다")
	@Pattern(regexp = "^\\d{4}$", message = "비공개 시 비밀번호는 숫자 4자리가 필요합니다")
	String password
) {}
