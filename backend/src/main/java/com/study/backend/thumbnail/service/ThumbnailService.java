package com.study.backend.thumbnail.service;

import java.nio.file.Path;

import com.study.backend.thumbnail.dto.SourceImage;
import com.study.backend.thumbnail.model.ThumbnailMetaData;

public interface ThumbnailService {
	Path saveThumbnail(SourceImage sourceImage, Long boardId);
	ThumbnailMetaData getThumbnailByBoardId(Long boardId);
	void deleteThumbnail(Long boardId);
}
