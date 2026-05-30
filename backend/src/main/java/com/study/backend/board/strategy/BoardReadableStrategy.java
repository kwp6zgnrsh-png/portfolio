package com.study.backend.board.strategy;

import java.util.List;

import com.study.backend.board.model.Board;
import com.study.backend.board.model.Page;
import com.study.backend.board.model.Search;

public interface BoardReadableStrategy {
	List<Board> searchPostList(Search search);
	Integer getPostCount(Search search);
	Object assembleListResponse(List<Board> boardList, Page page);
	Object assembleDetailResponse(Board board, Long memberId);
	void updateViews(Long boardId);
	Board getPostById(Long boardId);
	List<Board> getPostList();
}
