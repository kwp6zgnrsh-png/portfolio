package com.study.backend.board.dto.common;

public record BoardCreateResult(boolean fileUploadFailed) {

	private static final BoardCreateResult SUCCESS = new BoardCreateResult(false);
	private static final BoardCreateResult FILE_UPLOAD_FAILED = new BoardCreateResult(true);

	public static BoardCreateResult success() {
		return SUCCESS;
	}

	public static BoardCreateResult withFileUploadFailure() {
		return FILE_UPLOAD_FAILED;
	}

	public String message() {
		return fileUploadFailed
			? "게시글은 등록됐지만 파일 업로드에 실패했습니다."
			: "성공";
	}
}
