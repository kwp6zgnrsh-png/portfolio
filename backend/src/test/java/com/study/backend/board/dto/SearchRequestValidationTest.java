package com.study.backend.board.dto;

import static org.assertj.core.api.Assertions.*;

import java.time.LocalDate;
import java.util.Set;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import com.study.backend.board.dto.common.request.SearchRequest;

import jakarta.validation.ConstraintViolation;
import jakarta.validation.Validation;
import jakarta.validation.Validator;

class SearchRequestValidationTest {

	private static final String COMPLETE_MESSAGE = "시작일과 종료일을 함께 입력해 주세요.";
	private static final String ORDER_MESSAGE = "시작일은 종료일보다 늦을 수 없습니다.";

	private Validator validator;

	@BeforeEach
	void setUp() {
		validator = Validation.buildDefaultValidatorFactory().getValidator();
	}

	@Test
	@DisplayName("시작일과 종료일을 모두 생략하면 검증을 통과한다")
	void noDateRange_passes() {
		Set<ConstraintViolation<SearchRequest>> violations = validator.validate(request(null, null));

		assertThat(violations).noneMatch(violation -> isDateRangeMessage(violation.getMessage()));
	}

	@Test
	@DisplayName("시작일만 입력하면 검증에 실패한다")
	void startDateOnly_fails() {
		Set<ConstraintViolation<SearchRequest>> violations = validator.validate(
			request(LocalDate.of(2026, 7, 1), null)
		);

		assertThat(violations).anyMatch(violation -> COMPLETE_MESSAGE.equals(violation.getMessage()));
	}

	@Test
	@DisplayName("종료일만 입력하면 검증에 실패한다")
	void endDateOnly_fails() {
		Set<ConstraintViolation<SearchRequest>> violations = validator.validate(
			request(null, LocalDate.of(2026, 7, 15))
		);

		assertThat(violations).anyMatch(violation -> COMPLETE_MESSAGE.equals(violation.getMessage()));
	}

	@Test
	@DisplayName("시작일이 종료일보다 늦으면 검증에 실패한다")
	void reversedDateRange_fails() {
		Set<ConstraintViolation<SearchRequest>> violations = validator.validate(
			request(LocalDate.of(2026, 7, 15), LocalDate.of(2026, 7, 1))
		);

		assertThat(violations).anyMatch(violation -> ORDER_MESSAGE.equals(violation.getMessage()));
	}

	@Test
	@DisplayName("시작일과 종료일이 같아도 검증을 통과한다")
	void sameDateRange_passes() {
		Set<ConstraintViolation<SearchRequest>> violations = validator.validate(
			request(LocalDate.of(2026, 7, 15), LocalDate.of(2026, 7, 15))
		);

		assertThat(violations).noneMatch(violation -> isDateRangeMessage(violation.getMessage()));
	}

	private SearchRequest request(LocalDate startDate, LocalDate endDate) {
		return new SearchRequest(startDate, endDate, null, null, null, null, null, null, null);
	}

	private boolean isDateRangeMessage(String message) {
		return COMPLETE_MESSAGE.equals(message) || ORDER_MESSAGE.equals(message);
	}
}
