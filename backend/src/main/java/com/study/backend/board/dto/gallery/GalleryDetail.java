package com.study.backend.board.dto.gallery;

import lombok.Builder;

@Builder
public record GalleryDetail(
	Long id,
	String categoryName,
	String content,
	String createdDate,
	Integer views,
	String title,
	String author
) {}
