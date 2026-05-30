package com.study.backend.board.dto.inquiry;

import lombok.Builder;

@Builder
public record InquiryDetail(
	Long id,
	String content,
	String createdDate,
	Integer views,
	String title,
	String author,
	Long memberId
) {}
