package com.study.backend.comment.exception;

public class CommentTargetNotAllowedException extends RuntimeException {
	public CommentTargetNotAllowedException(String message) {
		super(message);
	}
}
