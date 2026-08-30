package com.study.backend.board.dto.inquiry;

import lombok.Builder;

@Builder
public record InquiryUpdate(
	Long id,
	String title,
	String content,
	Boolean isSecret,
	Integer version
) {}
