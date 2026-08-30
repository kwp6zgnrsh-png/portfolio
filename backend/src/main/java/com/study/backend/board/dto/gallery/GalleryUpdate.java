package com.study.backend.board.dto.gallery;

import lombok.Builder;

@Builder
public record GalleryUpdate(
	Long id,
	String title,
	String content,
	Long categoryId,
	Integer version
) {}
