package com.study.backend.board.dto.home;

import lombok.Builder;

@Builder
public record HomeFreeBoard(
	Long id,
	String categoryName,
	String title,
	String createdDate,
	Integer fileCount,
	Integer commentCount
) {}
