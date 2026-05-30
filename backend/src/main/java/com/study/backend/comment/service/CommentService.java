package com.study.backend.comment.service;

import java.util.List;

import com.study.backend.comment.model.Comment;

public interface CommentService {
	List<Comment> getCommentsByBoardId(Long boardId);
	Comment createComment(Comment comment);
	void deleteComment(Long commentId, Long memberId);
}
