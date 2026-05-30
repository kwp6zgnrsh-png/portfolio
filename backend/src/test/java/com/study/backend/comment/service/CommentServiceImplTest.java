package com.study.backend.comment.service;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.BDDMockito.*;

import java.util.List;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import com.study.backend.board.model.Board;
import com.study.backend.comment.exception.CommentNotFoundException;
import com.study.backend.comment.exception.CommentPermissionDeniedException;
import com.study.backend.comment.exception.CommentTargetNotAllowedException;
import com.study.backend.comment.mapper.CommentMapper;
import com.study.backend.comment.model.Comment;

@ExtendWith(MockitoExtension.class)
class CommentServiceImplTest {

	@Mock CommentMapper commentMapper;

	@InjectMocks CommentServiceImpl commentService;

	@Test
	@DisplayName("댓글 조회는 매퍼 결과를 그대로 반환한다")
	void getCommentsByBoardId_returnsMapperResult() {
		Comment comment = comment(1L, 1L, 1L);
		given(commentMapper.getCommentsByBoardId(1L)).willReturn(List.of(comment));

		List<Comment> result = commentService.getCommentsByBoardId(1L);

		assertThat(result).containsExactly(comment);
	}

	@Test
	@DisplayName("삭제된 게시글에는 댓글을 작성할 수 없다")
	void createComment_deletedBoard_throws() {
		given(commentMapper.getBoardForComment(1L)).willReturn(Board.builder()
			.id(1L)
			.boardTypeId(2L)
			.state(true)
			.build());

		assertThatThrownBy(() -> commentService.createComment(comment(1L, 1L, 1L)))
			.isInstanceOf(CommentTargetNotAllowedException.class)
			.hasMessageContaining("댓글을 작성할 수 없는 게시글입니다.");
	}

	@Test
	@DisplayName("댓글을 지원하지 않는 게시판에는 댓글을 작성할 수 없다")
	void createComment_unsupportedBoardType_throws() {
		given(commentMapper.getBoardForComment(1L)).willReturn(Board.builder()
			.id(1L)
			.boardTypeId(4L)
			.state(false)
			.build());

		assertThatThrownBy(() -> commentService.createComment(comment(1L, 1L, 1L)))
			.isInstanceOf(CommentTargetNotAllowedException.class)
			.hasMessageContaining("댓글을 작성할 수 없는 게시글입니다.");
	}

	@Test
	@DisplayName("자유게시판의 정상 게시글에는 댓글 작성이 성공한다")
	void createComment_supportedBoard_succeeds() {
		Comment comment = comment(1L, 1L, 1L);
		given(commentMapper.getBoardForComment(1L)).willReturn(Board.builder()
			.id(1L)
			.boardTypeId(2L)
			.state(false)
			.build());
		given(commentMapper.getCommentById(1L)).willReturn(comment);

		Comment saved = commentService.createComment(comment);

		assertThat(saved).isEqualTo(comment);
		then(commentMapper).should().createComment(comment);
	}

	@Test
	@DisplayName("존재하지 않는 댓글 삭제 시 예외를 던진다")
	void deleteComment_notFound_throws() {
		given(commentMapper.getCommentById(1L)).willReturn(null);

		assertThatThrownBy(() -> commentService.deleteComment(1L, 1L))
			.isInstanceOf(CommentNotFoundException.class);
	}

	@Test
	@DisplayName("작성자가 아닌 댓글 삭제 시 예외를 던진다")
	void deleteComment_notOwner_throws() {
		given(commentMapper.getCommentById(1L)).willReturn(comment(1L, 1L, 2L));

		assertThatThrownBy(() -> commentService.deleteComment(1L, 1L))
			.isInstanceOf(CommentPermissionDeniedException.class);
	}

	@Test
	@DisplayName("작성자 본인은 댓글을 삭제할 수 있다")
	void deleteComment_owner_succeeds() {
		given(commentMapper.getCommentById(1L)).willReturn(comment(1L, 1L, 1L));

		assertThatNoException().isThrownBy(() -> commentService.deleteComment(1L, 1L));
		then(commentMapper).should().deleteComment(1L, 1L);
	}

	private Comment comment(Long id, Long boardId, Long memberId) {
		return Comment.builder()
			.id(id)
			.boardId(boardId)
			.memberId(memberId)
			.content("댓글")
			.author("작성자")
			.build();
	}
}
