package com.study.backend.category.api;

import java.util.List;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.study.backend.category.model.Category;
import com.study.backend.category.service.CategoryService;
import com.study.backend.common.annotation.Public;
import com.study.backend.common.dto.ApiResponse;

import lombok.RequiredArgsConstructor;

@Public
@RestController
@RequestMapping("/api")
@RequiredArgsConstructor
public class CategoryApi {

	private final CategoryService categoryService;

	/** 게시판 타입에 해당하는 카테고리 목록을 반환한다. */
	@GetMapping("/categories")
	public ApiResponse<?> getCategories(@RequestParam String boardType){
		List<Category> categories = categoryService.getCategories(boardType);
		return ApiResponse.of("성공", categories);
	}
}
