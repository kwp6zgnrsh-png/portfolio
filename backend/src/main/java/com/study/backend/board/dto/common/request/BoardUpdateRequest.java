package com.study.backend.board.dto.common.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class BoardUpdateRequest {
	private Long categoryId;

	@NotBlank(message = "제목을 작성해주세요.")
	@Size(max = 100, message = "제목은 100자 미만입니다.")
	private String title;

	@NotBlank(message = "내용을 작성해주세요.")
	@Size(max = 4000, message = "내용은 4000자 미만입니다.")
	private String content;
	private String[] deleteFiles;
	private Boolean isSecret;

	@Pattern(regexp = "^$|^\\d{4}$", message = "비공개 시 비밀번호는 숫자 4자리가 필요합니다")
	private String secretPassword;

	@NotNull(message = "게시글 버전이 필요합니다.")
	private Integer version;
}
