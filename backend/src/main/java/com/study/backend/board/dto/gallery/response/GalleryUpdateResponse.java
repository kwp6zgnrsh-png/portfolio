package com.study.backend.board.dto.gallery.response;

import java.util.List;

import com.study.backend.board.dto.gallery.GalleryUpdate;
import com.study.backend.file.model.FileMetaData;

import lombok.Builder;

@Builder
public record GalleryUpdateResponse(
	GalleryUpdate galleryUpdate,
	List<FileMetaData> fileList
) {}
