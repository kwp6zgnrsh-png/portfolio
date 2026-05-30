package com.study.backend.board.strategy;

public interface BoardDeleteStrategy {
	void deletePost(Long boardId, Long memberId);
}
