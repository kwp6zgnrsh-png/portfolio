package com.study.backend.board.dto;

import static org.assertj.core.api.Assertions.*;

import java.time.LocalDate;
import java.time.ZoneId;
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
	@DisplayName("날짜를 모두 생략하면 서울 기준 오늘과 365일 전으로 설정된다")
	void noDateRange_defaultsToRecent365Days() {
		ZoneId zone = ZoneId.of("Asia/Seoul");
		LocalDate before = LocalDate.now(zone);

		SearchRequest search = request(null, null);

		LocalDate after = LocalDate.now(zone);

		// 테스트 도중 자정이 지나도 정상적으로 검증
		assertThat(search.endDate()).isBetween(before, after);
		assertThat(search.startDate())
			.isEqualTo(search.endDate().minusDays(365));

		assertThat(validator.validate(search)).isEmpty();
	}

	@Test
	@DisplayName("날짜 차이가 정확히 365일이면 통과하고 입력 날짜를 유지한다")
	void dateRange365Days_passes() {
		LocalDate start = LocalDate.of(2025, 1, 1);
		LocalDate end = start.plusDays(365);

		SearchRequest search = request(start, end);

		assertThat(search.startDate()).isEqualTo(start);
		assertThat(search.endDate()).isEqualTo(end);
		assertThat(validator.validate(search)).isEmpty();
	}

	@Test
	@DisplayName("날짜 차이가 366일이면 검증에 실패한다")
	void dateRange366Days_fails() {
		LocalDate start = LocalDate.of(2025, 1, 1);
		SearchRequest search = request(start, start.plusDays(366));

		assertThat(validator.validate(search))
			.anySatisfy(violation -> {
				assertThat(violation.getPropertyPath().toString())
					.isEqualTo("dateRangeWithinLimit");
				assertThat(violation.getMessage())
					.isEqualTo("최대 검색 범위는 365일입니다.");
			});
	}

	@Test
	@DisplayName("윤년을 포함해 366일인 기간은 달력상 1년이어도 실패한다")
	void dateRangeAcrossLeapYear_fails() {
		SearchRequest search = request(
			LocalDate.of(2024, 1, 1),
			LocalDate.of(2025, 1, 1)
		);

		assertThat(validator.validate(search))
			.anyMatch(violation ->
				violation.getPropertyPath().toString()
					.equals("dateRangeWithinLimit")
			);
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
		assertThat(violations).isEmpty();
	}

	private SearchRequest request(LocalDate startDate, LocalDate endDate) {
		return new SearchRequest(startDate, endDate, null, null, null, null, null, null, null);
	}

}
