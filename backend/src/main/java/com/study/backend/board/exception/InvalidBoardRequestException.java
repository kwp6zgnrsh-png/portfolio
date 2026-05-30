package com.study.backend.board.exception;

public class InvalidBoardRequestException extends RuntimeException {
	public InvalidBoardRequestException(String message) {
		super(message);
	}
}
