package com.study.backend.board.model;

import com.study.backend.board.exception.BoardTypeException;
import com.study.backend.category.model.CategoryType;

/**
 * - NOTICE: 공지사항
 * - FREE: 자유게시판
 * - GALLERY: 갤러리
 * - INQUIRY: 문의게시판
 */
public enum BoardType {
	NOTICES(1L,   CategoryType.MANAGER),
	BOARDS(2L,    CategoryType.MEMBER),
	GALLERIES(3L, CategoryType.MEMBER),
	INQUIRIES(4L, CategoryType.MEMBER);

	private final Long id;
	private final CategoryType categoryType;

	BoardType(Long id, CategoryType categoryType) {
		this.id = id;
		this.categoryType = categoryType;
	}

	public Long id() {
		return id;
	}

	public CategoryType categoryType() {
		return categoryType;
	}

	public boolean supportsCategory() {
		return this == NOTICES || this == BOARDS || this == GALLERIES;
	}

	public boolean supportsComment() {
		return this == BOARDS;
	}

	public boolean supportsCreate() {
		return this != NOTICES;
	}

	public boolean supportsUpdate() {
		return this != NOTICES;
	}

	public boolean supportsDelete() {
		return this != NOTICES;
	}

	public boolean supportsFile() {
		return this == BOARDS || this == GALLERIES;
	}

	public boolean supportsThumbnail() {
		return this == GALLERIES;
	}

	public boolean supportsSecretPost() {
		return this == INQUIRIES;
	}

	public static Long idOf(String boardType) {
		return from(boardType).id();
	}

	public static BoardType fromId(Long boardTypeId) {
		for (BoardType value : values()) {
			if (value.id.equals(boardTypeId)) {
				return value;
			}
		}
		throw new BoardTypeException("지원하지 않는 게시판입니다: " + boardTypeId);
	}

	public static BoardType from(String boardType) {
		try {
			return BoardType.valueOf(boardType.toUpperCase());
		} catch (IllegalArgumentException e) {
			throw new BoardTypeException("지원하지 않는 게시판입니다: " + boardType);
		}
	}
}
