package com.study.backend.comment.dto.response;

import lombok.Builder;

@Builder
public record CommentResponse(
	Long id,
	String author,
	String content,
	String createdDate,
	boolean isMyComment
) {
}
