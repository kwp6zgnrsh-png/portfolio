package com.study.backend.comment.mapper;

import java.util.List;

import org.apache.ibatis.annotations.Mapper;

import com.study.backend.board.model.Board;
import com.study.backend.comment.model.Comment;

@Mapper
public interface CommentMapper {
	List<Comment> getCommentsByBoardId(Long boardId);
	void createComment(Comment comment);
	void deleteComment(Long commentId, Long memberId);
	Comment	getCommentById(Long commentId);
	Board getBoardForComment(Long boardId);
}
