package com.study.backend.board.exception;

public class BoardConflictException extends RuntimeException {
	public BoardConflictException(String message) {
		super(message);
	}
}
