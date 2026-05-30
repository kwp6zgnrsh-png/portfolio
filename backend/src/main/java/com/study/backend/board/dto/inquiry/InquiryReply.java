package com.study.backend.board.dto.inquiry;

import lombok.Builder;

@Builder
public record InquiryReply(
	Long id,
	String content,
	String createdDate,
	String author
) {}
