package com.study.backend.thumbnail.mapper;

import org.apache.ibatis.annotations.Mapper;

import com.study.backend.thumbnail.model.ThumbnailMetaData;

@Mapper
public interface ThumbnailMapper {
	void createThumbnail(ThumbnailMetaData thumbnail);
	ThumbnailMetaData getThumbnailByBoardId(Long boardId);
	void deleteThumbnail(Long boardId);
}
