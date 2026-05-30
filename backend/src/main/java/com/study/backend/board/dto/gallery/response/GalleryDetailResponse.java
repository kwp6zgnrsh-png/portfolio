package com.study.backend.board.dto.gallery.response;

import java.util.List;

import com.study.backend.board.dto.gallery.GalleryDetail;
import com.study.backend.file.model.FileMetaData;

import lombok.Builder;

@Builder
public record GalleryDetailResponse(
	GalleryDetail galleryDetail,
	List<FileMetaData> galleryImageList,
	Boolean isMyPost
) {}
