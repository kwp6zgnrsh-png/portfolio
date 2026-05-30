package com.study.backend.board.dto.home;

import lombok.Builder;

@Builder
public record HomeNotice(
	Long id,
	String categoryName,
	String title,
	String createdDate
) {}
