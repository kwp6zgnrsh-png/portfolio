package com.study.backend.board.dto.freeboard;

import lombok.Builder;

@Builder
public record FreeBoardList(
	Long id,
	String title,
	Integer views,
	String createdDate,
	String categoryName,
	String author,
	Integer commentCount,
	Integer fileCount
) {}
