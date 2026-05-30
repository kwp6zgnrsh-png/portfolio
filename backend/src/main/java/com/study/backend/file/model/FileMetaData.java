package com.study.backend.file.model;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class FileMetaData {
	private Long id;
	private String fileName;
	private String storeName;
	private String extension;
	private String path;
	private Long fileSize;
	private Long boardId;
}
