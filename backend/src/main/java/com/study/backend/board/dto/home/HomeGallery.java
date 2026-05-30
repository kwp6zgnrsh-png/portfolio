package com.study.backend.board.dto.home;

import lombok.Builder;

@Builder
public record HomeGallery(
	Long id,
	String categoryName,
	String createdDate,
	String storeName,
	String extension,
	String path,
	Integer fileCount
) {}
