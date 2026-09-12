package com.study.backend.board.model;

import java.time.LocalDate;

import lombok.Builder;
import lombok.Getter;
import lombok.Setter;

/**
 * - startDate: 날짜 시작 범위
 * - endDate: 날짜 끝 범위
 * - categoryId: 선택한 카테고리의 ID
 * - searchWord: 검색어
 * - page: 현재 페이지
 * - limit: 몇 개씩 볼지
 * - orderByField: 정렬 조건
 * - direction: 정렬(방향) 조건
 */
@Getter
@Setter
@Builder(toBuilder = true)
public class Search {
	private LocalDate startDate;
	private LocalDate endDate;
	private Long categoryId;
	private String searchWord;
	private Integer page;
	private Integer limit;
	private String orderByField;
	private String direction;
	private Boolean onlyMine;
	private Long memberId;

	/** 검색어의 특수문자를 이스케이프하여 부분 일치 검색 패턴을 만든다. */
	public String getSearchPattern() {
		if (searchWord == null || searchWord.isEmpty()) {
			return null;
		}

		String escaped = searchWord
			.replace("!", "!!")
			.replace("%", "!%")
			.replace("_", "!_");

		return "%" + escaped + "%";
	}
}
