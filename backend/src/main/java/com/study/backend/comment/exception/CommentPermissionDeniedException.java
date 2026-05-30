package com.study.backend.comment.exception;

public class CommentPermissionDeniedException extends RuntimeException {
	public CommentPermissionDeniedException(String message) {
		super(message);
	}
}
