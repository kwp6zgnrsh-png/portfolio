package com.study.backend.file.cleanup.model;

import java.time.Instant;

public record OrphanFileCandidate(String relativePath, long fileSize, Instant lastModifiedAt, Reason reason) {

	public enum Reason {
		// DB 메타데이터에도, 대기 작업에도 없는 파일
		UNREFERENCED,
		// 원본 메타데이터에 삭제 표시가 있으나 대기 작업이 없는 파일
		DELETED_METADATA
	}
}