package com.study.backend.board.strategy;

import java.util.List;

import org.springframework.stereotype.Service;

import com.study.backend.board.assembler.NoticeResponseAssembler;
import com.study.backend.board.model.Board;
import com.study.backend.board.model.BoardType;
import com.study.backend.board.model.Page;
import com.study.backend.board.model.Search;
import com.study.backend.board.service.NoticeService;

import lombok.RequiredArgsConstructor;

@Service("noticesStrategy")
@RequiredArgsConstructor
public class NoticeStrategy implements BoardReadableStrategy {

	private final NoticeService boardService;
	private final NoticeResponseAssembler responseAssembler;

	@Override
	public List<Board> searchPostList(Search search) {
		return boardService.searchPostList(search, BoardType.NOTICES.id());
	}

	@Override
	public Integer getPostCount(Search search) {
		return boardService.getPostCountByCriteria(search, BoardType.NOTICES.id());
	}

	@Override
	public Object assembleListResponse(List<Board> boardList, Page page) {
		return responseAssembler.assembleListResponse(boardList, page);
	}

	@Override
	public void updateViews(Long boardId) {
		boardService.updateViews(boardId);
	}

	@Override
	public Board getPostById(Long boardId) {
		return boardService.getPostById(boardId);
	}

	@Override
	public Object assembleDetailResponse(Board board, Long memberId) {
		return responseAssembler.assembleDetailResponse(board);
	}

	@Override
	public List<Board> getPostList() {
		return boardService.getPostList();
	}
}
