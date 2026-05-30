package com.study.backend.board.exception;

public class BoardNotFoundException extends RuntimeException{
	public BoardNotFoundException(String message) {
		super(message);
	}
}