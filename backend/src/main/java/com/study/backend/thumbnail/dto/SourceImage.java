package com.study.backend.thumbnail.dto;

import lombok.Builder;

@Builder
public record SourceImage(
	String fileName,
	String storeName,
	String extension,
	String path
) {
}
