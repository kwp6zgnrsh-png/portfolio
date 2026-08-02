package com.study.backend.category.service;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.BDDMockito.*;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import com.study.backend.board.exception.InvalidBoardRequestException;
import com.study.backend.category.mapper.CategoryMapper;
import com.study.backend.category.model.CategoryType;

@ExtendWith(MockitoExtension.class)
class CategoryServiceImplTest {

	@Mock CategoryMapper categoryMapper;
	@InjectMocks CategoryServiceImpl categoryService;

	@Test
	@DisplayName("카테고리를 선택하지 않으면 예외를 던진다")
	void validateCategory_nullId_throws() {
		assertThatThrownBy(() -> categoryService.validateCategory(null, CategoryType.MEMBER))
			.isInstanceOf(InvalidBoardRequestException.class)
			.hasMessage("카테고리를 선택해 주세요.");

		then(categoryMapper).shouldHaveNoInteractions();
	}

	@Test
	@DisplayName("요청한 유형에 속하는 카테고리면 검증을 통과한다")
	void validateCategory_matchingType_passes() {
		given(categoryMapper.existsByIdAndType(1L, "MEMBER")).willReturn(true);

		assertThatNoException().isThrownBy(() ->
			categoryService.validateCategory(1L, CategoryType.MEMBER)
		);
	}

	@Test
	@DisplayName("존재하지 않거나 유형이 다른 카테고리면 예외를 던진다")
	void validateCategory_missingOrDifferentType_throws() {
		given(categoryMapper.existsByIdAndType(5L, "MEMBER")).willReturn(false);

		assertThatThrownBy(() -> categoryService.validateCategory(5L, CategoryType.MEMBER))
			.isInstanceOf(InvalidBoardRequestException.class)
			.hasMessage("유효하지 않은 카테고리입니다.");
	}
}
