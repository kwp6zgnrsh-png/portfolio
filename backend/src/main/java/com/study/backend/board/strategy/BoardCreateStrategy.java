package com.study.backend.board.strategy;

import org.springframework.web.multipart.MultipartFile;

import com.study.backend.board.dto.common.BoardCreateResult;
import com.study.backend.board.model.Board;

public interface BoardCreateStrategy {
	BoardCreateResult createPost(Board board, Long boardTypeId, Long memberId, MultipartFile[] files);
}
