package com.study.backend.board.mapper;

import org.apache.ibatis.annotations.Mapper;

import com.study.backend.board.dto.common.request.BoardUpdateRequest;
import com.study.backend.board.model.Board;

@Mapper
public interface GalleryMapper extends BoardMapper {
	Board getPostForUpdate(Long boardId);

	void createPost(Board board, Long boardTypeId, Long memberId);

	void updatePost(Long boardId, BoardUpdateRequest board, Long memberId);

	void deletePost(Long boardId, Long memberId);
}
