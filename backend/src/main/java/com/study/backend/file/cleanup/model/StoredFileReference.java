package com.study.backend.file.cleanup.model;

import lombok.Getter;
import lombok.NoArgsConstructor;

@Getter
@NoArgsConstructor
public class StoredFileReference {

	private String path;
	private String storeName;
	private String extension;

	// true: 삭제 표시된 원본 파일
	// false 또는 null: 보수적으로 보존
	private Boolean deleted;
}