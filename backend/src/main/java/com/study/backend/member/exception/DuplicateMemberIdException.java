package com.study.backend.member.exception;

public class DuplicateMemberIdException extends RuntimeException {

	public DuplicateMemberIdException(String message) {
		super(message);
	}

	public DuplicateMemberIdException(String message, Throwable cause) {
		super(message, cause);
	}
}
