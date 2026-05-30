package com.study.backend.category.mapper;

import java.util.List;

import org.apache.ibatis.annotations.Mapper;

import com.study.backend.category.model.Category;

@Mapper
public interface CategoryMapper {
	List<Category> getCategories(String categoryType);
}
