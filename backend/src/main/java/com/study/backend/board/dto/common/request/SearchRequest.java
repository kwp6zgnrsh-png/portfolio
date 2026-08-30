package com.study.backend.board.dto.common.request;

import java.time.LocalDate;

import org.springframework.format.annotation.DateTimeFormat;

import jakarta.validation.constraints.AssertTrue;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.Size;

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
public record SearchRequest(

	@DateTimeFormat(iso = DateTimeFormat.ISO.DATE)
	LocalDate startDate,

	@DateTimeFormat(iso = DateTimeFormat.ISO.DATE)
	LocalDate endDate,

	Long categoryId,

	@Size(max = 100)
	String searchWord,

	@Min(1)
	Integer page,

	@Min(10) @Max(50)
	Integer limit,

	String orderByField,
	String direction,
	Boolean onlyMine
) {

	public SearchRequest {
		if (page == null) {
			page = 1;
		}
		if (limit == null) {
			limit = 10;
		}
	}

	@AssertTrue(message = "시작일과 종료일을 함께 입력해 주세요.")
	public boolean isDateRangeComplete() {
		return (startDate == null) == (endDate == null);
	}

	@AssertTrue(message = "시작일은 종료일보다 늦을 수 없습니다.")
	public boolean isDateRangeOrdered() {
		return startDate == null || endDate == null || !startDate.isAfter(endDate);
	}
}
