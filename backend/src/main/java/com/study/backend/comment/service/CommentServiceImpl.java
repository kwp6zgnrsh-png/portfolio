package com.study.backend.comment.service;

import java.util.List;
import java.util.Objects;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.study.backend.board.model.Board;
import com.study.backend.board.model.BoardType;
import com.study.backend.comment.exception.CommentNotFoundException;
import com.study.backend.comment.exception.CommentPermissionDeniedException;
import com.study.backend.comment.exception.CommentTargetNotAllowedException;
import com.study.backend.comment.mapper.CommentMapper;
import com.study.backend.comment.model.Comment;

import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
public class CommentServiceImpl implements CommentService {

	private final CommentMapper commentMapper;

	/** 특정 게시글의 댓글 목록을 조회한다. */
	@Override
	public List<Comment> getCommentsByBoardId(Long boardId) {
		return commentMapper.getCommentsByBoardId(boardId);
	}

	/** 댓글을 저장하고 반환한다. */
	@Override
	@Transactional
	public Comment createComment(Comment comment) {
		Board board = commentMapper.getBoardForComment(comment.getBoardId());
		if (board == null || Boolean.TRUE.equals(board.getState())) {
			throw new CommentTargetNotAllowedException("댓글을 작성할 수 없는 게시글입니다.");
		}
		BoardType boardType = BoardType.fromId(board.getBoardTypeId());
		if (!boardType.supportsComment()) {
			throw new CommentTargetNotAllowedException("댓글을 작성할 수 없는 게시글입니다.");
		}
		commentMapper.createComment(comment);
		// author(회원명), createdDate(DB 기본값)를 반영하기 위해 재조회
		return commentMapper.getCommentById(comment.getId());
	}

	/** 댓글을 삭제한다. 작성자 본인만 가능하다. */
	@Override
	@Transactional
	public void deleteComment(Long commentId, Long memberId) {
		Comment comment = commentMapper.getCommentById(commentId);
		if (comment == null) {
			throw new CommentNotFoundException("존재하지 않는 댓글입니다.");
		}
		if (!Objects.equals(comment.getMemberId(), memberId)) {
			throw new CommentPermissionDeniedException("삭제할 권한이 없습니다.");
		}
		commentMapper.deleteComment(commentId, memberId);
	}
}
