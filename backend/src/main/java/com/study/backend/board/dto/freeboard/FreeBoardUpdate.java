package com.study.backend.board.dto.freeboard;

import lombok.Builder;

@Builder
public record FreeBoardUpdate(
	Long id,
	String title,
	String content,
	Long categoryId
) {}
