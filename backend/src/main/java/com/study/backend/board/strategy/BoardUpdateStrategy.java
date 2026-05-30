package com.study.backend.board.strategy;

import org.springframework.web.multipart.MultipartFile;

import com.study.backend.board.dto.common.request.BoardUpdateRequest;
import com.study.backend.board.model.Board;

public interface BoardUpdateStrategy {
	void updatePost(Long boardId, BoardUpdateRequest board, Long memberId, MultipartFile[] files);
	Object assembleUpdateFormResponse(Board board);
	Board getPostForUpdate(Long boardId, Long memberId);
}
