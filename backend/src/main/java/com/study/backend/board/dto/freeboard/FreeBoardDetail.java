package com.study.backend.board.dto.freeboard;

import lombok.Builder;

@Builder
public record FreeBoardDetail(
	Long id,
	String categoryName,
	String content,
	String createdDate,
	Integer views,
	String title,
	String author,
	Long memberId
) {}
