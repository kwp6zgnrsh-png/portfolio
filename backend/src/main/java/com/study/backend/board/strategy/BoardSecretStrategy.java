package com.study.backend.board.strategy;

import com.study.backend.board.model.Board;

public interface BoardSecretStrategy {
	boolean matchesSecretPassword(Long boardId, String rawPassword);
	boolean isPostOwner(Long boardId, Long memberId);
	void verifySecretPassword(Long boardId, String rawPassword);
	void validateAccess(Board board, Long memberId, String secretToken);
}
