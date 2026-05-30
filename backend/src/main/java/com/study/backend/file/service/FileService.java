package com.study.backend.file.service;

import java.nio.file.Path;
import java.util.List;

import org.springframework.web.multipart.MultipartFile;

import com.study.backend.file.model.DownloadFile;
import com.study.backend.file.model.FileMetaData;

public interface FileService {
	List<Path> createFiles(Long boardId, MultipartFile[] files);

	List<FileMetaData> getFilesByBoardId(Long boardId);

	FileMetaData getFileById(Long fileId, Long boardTypeId);

	DownloadFile getDownloadFile(Long fileId, Long boardTypeId);

	FileMetaData getFirstFileByBoardId(Long boardId);

	void deleteFiles(Long boardId, String[] deleteFileIds);

	void deleteAllFilesByBoardId(Long boardId);

	void validateFiles(MultipartFile[] files);

	void validateFileCountForUpdate(Long boardId, String[] deleteFileIds, MultipartFile[] files);

	String resolveAbsolutePath(String path);
}
