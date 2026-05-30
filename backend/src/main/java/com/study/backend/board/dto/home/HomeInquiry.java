package com.study.backend.board.dto.home;

import lombok.Builder;

@Builder
public record HomeInquiry(
	Long id,
	String title,
	String createdDate,
	Boolean isSecret,
	Integer answerCount
) {}
