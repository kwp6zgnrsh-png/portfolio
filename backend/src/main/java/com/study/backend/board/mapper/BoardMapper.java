package com.study.backend.board.mapper;

import java.util.List;

import com.study.backend.board.model.Board;
import com.study.backend.board.model.Search;

public interface BoardMapper {
	List<Board> searchPostList(Search search, Long boardTypeId,Integer offset);

	Integer getPostCountByCriteria(Search search, Long boardTypeId);

	Board getPostById(Long boardId);

	void updateViews(Long boardId);

	List<Board> getPostList(Long boardTypeId);
}
