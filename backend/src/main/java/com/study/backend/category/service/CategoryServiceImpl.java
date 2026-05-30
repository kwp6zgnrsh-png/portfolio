package com.study.backend.category.service;

import java.util.List;

import org.springframework.stereotype.Service;

import com.study.backend.board.exception.BoardTypeException;
import com.study.backend.board.model.BoardType;
import com.study.backend.category.mapper.CategoryMapper;
import com.study.backend.category.model.Category;
import com.study.backend.category.model.CategoryType;

import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
public class CategoryServiceImpl implements CategoryService {

	private final CategoryMapper categoryMapper;

	@Override
	public List<Category> getCategories(String boardType) {
		BoardType type = BoardType.from(boardType);
		if (!type.supportsCategory()) {
			throw new BoardTypeException("카테고리를 지원하지 않는 게시판입니다: " + boardType);
		}
		return getCategories(type.categoryType());
	}

	@Override
	public List<Category> getCategories(CategoryType categoryType) {
		return categoryMapper.getCategories(categoryType.name());
	}
}
