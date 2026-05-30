package com.study.backend.board.dto.gallery.response;

import java.util.List;

import com.study.backend.board.dto.gallery.GalleryList;
import com.study.backend.board.model.Page;

import lombok.Builder;

/**
 * - galleryBoards: 갤러리 게시글
 * - page: 페이징 정보
 */
@Builder
public record GalleryResponse(
	List<GalleryList> galleryList,
	Page page
) {}
