package com.study.backend.comment.dto.request;

import com.study.backend.comment.model.Comment;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

public record CommentRequest(
	@NotBlank
	@Size(max = 250, message = "댓글은 250자 이하입니다.")
	String content,

	@NotNull
	Long boardId
) {
	public Comment toComment(Long memberId) {
		return Comment.builder()
			.content(content)
			.boardId(boardId)
			.memberId(memberId)
			.build();
	}
}
