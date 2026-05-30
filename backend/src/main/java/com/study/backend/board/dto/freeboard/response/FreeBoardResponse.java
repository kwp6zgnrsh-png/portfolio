package com.study.backend.board.dto.freeboard.response;

import java.util.List;

import com.study.backend.board.dto.freeboard.FreeBoardList;
import com.study.backend.board.model.Page;

import lombok.Builder;

/**
 * - freeBoards: 자유게시판 게시글
 * - page: 페이징 정보
 */
@Builder
public record FreeBoardResponse(
	List<FreeBoardList> freeBoardList,
	Page page
) {}
