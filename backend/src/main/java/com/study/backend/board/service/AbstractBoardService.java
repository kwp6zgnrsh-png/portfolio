package com.study.backend.board.service;

import java.util.List;
import java.util.Objects;

import com.study.backend.board.exception.BoardNotFoundException;
import com.study.backend.board.exception.BoardPermissionDeniedException;
import com.study.backend.board.mapper.BoardMapper;
import com.study.backend.board.model.Board;
import com.study.backend.board.model.BoardType;
import com.study.backend.board.model.Search;

import lombok.RequiredArgsConstructor;

@RequiredArgsConstructor
public abstract class AbstractBoardService<T extends BoardMapper> {

	protected final T mapper;

	protected abstract BoardType boardType();

	/** 검색 조건과 게시판 타입 ID로 게시글 목록을 조회한다. */
	public List<Board> searchPostList(Search search, Long boardTypeId) {
		int page = safePage(search);
		long offset = Math.multiplyExact((long) (page - 1), search.getLimit());
		return mapper.searchPostList(search, boardTypeId, offset);
	}

	/** 검색 조건과 게시판 타입 ID에 맞는 게시글 총 개수를 반환한다. */
	public Integer getPostCountByCriteria(Search search, Long boardTypeId) {
		return mapper.getPostCountByCriteria(search, boardTypeId);
	}

	/** ID로 게시글 단건을 조회한다. 존재하지 않으면 예외를 던진다. */
	public Board getPostById(Long boardId) {
		Board board = mapper.getPostById(boardId);
		validateBoardExistsAndType(board);
		return board;
	}

	/** 게시글 조회수를 1 증가시킨다. */
	public void updateViews(Long boardId) {
		mapper.updateViews(boardId);
	}

	/** 홈 화면 노출용 게시글 목록을 조회한다. */
	public List<Board> getPostList() {
		return mapper.getPostList(boardType().id());
	}

	/** 페이지 번호가 null이거나 1 미만이면 1을 반환한다. */
	private int safePage(Search search) {
		return (search.getPage() == null || search.getPage() < 1) ? 1 : search.getPage();
	}

	/** 게시글 존재 여부와 게시판 타입 일치 여부를 검증한다.*/
	private void validateBoardExistsAndType(Board board) {
		if (board == null || !boardType().id().equals(board.getBoardTypeId())) {
			throw new BoardNotFoundException("존재하지 않는 게시글입니다.");
		}
	}

	/** 게시글 존재 여부와 작성자 일치 여부를 검증한다. */
	protected void validateOwnership(Board board, Long memberId, String permissionDeniedMessage) {
		validateBoardExistsAndType(board);
		if (memberId == null || !Objects.equals(board.getMemberId(), memberId)) {
			throw new BoardPermissionDeniedException(permissionDeniedMessage);
		}
	}

}
