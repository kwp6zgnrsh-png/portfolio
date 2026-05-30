package com.study.backend.board.exception;

public class BoardPermissionDeniedException extends RuntimeException{
	public BoardPermissionDeniedException(String message) {
		super(message);
	}
}