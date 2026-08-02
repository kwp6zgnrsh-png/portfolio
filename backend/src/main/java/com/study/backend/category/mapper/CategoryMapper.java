package com.study.backend.category.mapper;

import java.util.List;

import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import com.study.backend.category.model.Category;

@Mapper
public interface CategoryMapper {
	List<Category> getCategories(String categoryType);
	boolean existsByIdAndType(@Param("categoryId") Long categoryId,
							  @Param("categoryType") String categoryType);
}
