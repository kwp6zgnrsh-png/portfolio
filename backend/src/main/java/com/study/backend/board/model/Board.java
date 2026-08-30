package com.study.backend.board.model;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * MyBatis 쿼리 결과 매핑 객체 (도메인 모델 아님).
 * 각 게시판 타입의 쿼리 결과를 하나의 객체로 받아 Strategy 내부에서 타입별 DTO로 변환된다.
 * 외부(Controller/Client)에 직접 노출되지 않는다.
 * 필드별 사용 게시판:
 * - commentCount, fileCount : 자유게시판
 * - isSecret, answer, secretPassword : 문의게시판
 * - storeName, extension, path : 갤러리
 */
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class Board {
	private Long id;
	private String title;
	private String content;
	private Integer views;
	private String createdDate;
	private String categoryName;
	private String author;
	private Long memberId;

	private Integer commentCount;
	private Integer fileCount;

	private Boolean isSecret;
	private String secretPassword;
	private Integer answerCount;

	private String storeName;
	private String extension;
	private String path;

	private Long categoryId;
	private Long boardTypeId;
	private Boolean state;
	private Integer version;
}
