package com.study.backend.file.service;

import java.util.EnumMap;
import java.util.Map;

import org.springframework.stereotype.Component;

import com.study.backend.board.exception.BoardTypeException;
import com.study.backend.board.model.BoardType;

@Component
public class FileServiceFactory {

	private static final Map<BoardType, String> FILE_SERVICE_BEAN_NAMES = new EnumMap<>(BoardType.class);

	static {
		FILE_SERVICE_BEAN_NAMES.put(BoardType.BOARDS, "freeBoardFileService");
		FILE_SERVICE_BEAN_NAMES.put(BoardType.GALLERIES, "galleryFileService");
	}

	private final Map<String, FileService> fileServices;

	public FileServiceFactory(Map<String, FileService> fileServices) {
		this.fileServices = fileServices;
	}

	/** 게시판 타입 문자열로 해당하는 FileService 구현체를 반환한다. */
	public FileService getFileService(String boardType) {
		return getFileService(BoardType.from(boardType));
	}

	/** 게시판 타입 enum으로 해당하는 FileService 구현체를 반환한다. */
	public FileService getFileService(BoardType boardType) {
		if (!boardType.supportsFile()) {
			throw new BoardTypeException("파일 서비스를 지원하지 않는 게시판입니다: " + boardType.name());
		}
		String serviceName = FILE_SERVICE_BEAN_NAMES.get(boardType);
		FileService fileService = fileServices.get(serviceName);
		if (fileService == null) {
			throw new BoardTypeException("파일 서비스를 지원하지 않는 게시판입니다: " + boardType.name());
		}
		return fileService;
	}
}
