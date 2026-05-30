package com.study.backend.board.dto.inquiry;

import lombok.Builder;

@Builder
public record InquiryList(
	Long id,
	String title,
	Integer views,
	String createdDate,
	String author,
	Boolean isSecret,
	Integer answerCount
) {}
