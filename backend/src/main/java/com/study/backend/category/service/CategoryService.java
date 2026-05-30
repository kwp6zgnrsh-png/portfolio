package com.study.backend.category.service;

import java.util.List;

import com.study.backend.category.model.Category;
import com.study.backend.category.model.CategoryType;

public interface CategoryService {
	List<Category> getCategories(String boardType);
	List<Category> getCategories(CategoryType categoryType);
}
