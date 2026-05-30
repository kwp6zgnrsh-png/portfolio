package com.study.backend.file.service;

import java.util.Set;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import org.springframework.context.ApplicationEventPublisher;

import com.study.backend.file.mapper.FileMapper;
import com.study.backend.file.model.FileTypes;

@Service("freeBoardFileService")
public class FreeBoardFileServiceImpl extends AbstractFileService {

	@Value("${store.base-path}")
	private String storePath;

	@Value("${free-board.path}")
	private String path;

	@Value("${free-board.deleted-path}")
	private String deletedPath;

	@Value("${free-board.volume}")
	private Long volume;

	@Value("${free-board.max-file-count}")
	private int maxFileCount;

	public FreeBoardFileServiceImpl(FileMapper fileMapper, ApplicationEventPublisher eventPublisher) {
		super(fileMapper, eventPublisher);
	}

	@Override
	public String resolveAbsolutePath(String path) {
		return joinStorePath(storePath, path);
	}

	@Override
	protected String getPath() {
		return path;
	}

	@Override
	protected String getDeletedPath() {
		return deletedPath;
	}

	/** 자유게시판 파일 최대 용량(바이트)을 반환한다. */
	@Override
	protected Long getFileMaxSize() {
		return volume;
	}

	/** 자유게시판에서 허용되는 파일 MIME 서브타입 목록을 반환한다. */
	@Override
	protected Set<String> getFileTypes() {
		return FileTypes.freeBoardFileTypes();
	}

	@Override
	protected int getMaxFileCount() {
		return maxFileCount;
	}
}
