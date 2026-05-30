package com.study.backend.board.dto.gallery;

import lombok.Builder;

@Builder
public record GalleryList(
	Long id,
	String title,
	String content,
	String author,
	String createdDate,
	String categoryName,
	String storeName,
	String extension,
	String path
) {}
