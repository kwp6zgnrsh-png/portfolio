package com.study.backend.common.exception;

public class BoardQueryUnavailableException extends RuntimeException {
	public BoardQueryUnavailableException(Throwable cause) {
		super("일시적으로 조회할 수 없습니다. 잠시 후 다시 시도해 주세요.", cause);
	}
}
