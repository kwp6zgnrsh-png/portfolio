package com.study.backend.comment.api;

import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.study.backend.comment.dto.request.CommentRequest;
import com.study.backend.comment.dto.response.CommentResponse;
import com.study.backend.comment.model.Comment;
import com.study.backend.comment.service.CommentService;
import com.study.backend.common.annotation.LoginMember;
import com.study.backend.common.dto.ApiResponse;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;

@RestController
@RequestMapping("/api")
@RequiredArgsConstructor
public class CommentApi {

	private final CommentService commentService;

	/** 댓글을 등록하고 저장된 댓글 정보를 반환한다. */
	@PostMapping("/comment")
	public ApiResponse<?> createComment(@RequestBody @Valid CommentRequest commentRequest,
									 @LoginMember Long memberId) {

		Comment comment = commentService.createComment(commentRequest.toComment(memberId));
		CommentResponse savedComment = CommentResponse.builder()
			.id(comment.getId())
			.author(comment.getAuthor())
			.content(comment.getContent())
			.createdDate(comment.getCreatedDate())
			.isMyComment(true)
			.build();

		return ApiResponse.of("댓글 등록", savedComment);
	}

	/** 댓글을 삭제한다. */
	@DeleteMapping("/comment/{id}")
	public ApiResponse<?> deleteComment(@PathVariable("id") Long commentId,
									 @LoginMember Long memberId) {

		commentService.deleteComment(commentId, memberId);

		return ApiResponse.of("댓글 삭제");
	}
}
