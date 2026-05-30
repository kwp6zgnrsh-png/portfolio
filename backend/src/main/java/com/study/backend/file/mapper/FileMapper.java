package com.study.backend.file.mapper;

import java.util.List;

import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import com.study.backend.file.model.FileMetaData;

@Mapper
public interface FileMapper {
	void createFile(FileMetaData file);
	List<FileMetaData> getFilesByBoardId(Long boardId);
	FileMetaData getFileByIdAndBoardType(@Param("fileId") Long fileId, @Param("boardTypeId") Long boardTypeId);
	void deleteFileById(Long fileId);
	FileMetaData getFirstFileByBoardId(Long boardId);
}
