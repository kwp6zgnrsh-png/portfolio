package com.study.backend.file.service;

import java.util.Set;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import org.springframework.context.ApplicationEventPublisher;

import com.study.backend.file.mapper.FileMapper;
import com.study.backend.file.model.FileTypes;

@Service("galleryFileService")
public class GalleryFileServiceImpl extends AbstractFileService {

	@Value("${store.base-path}")
	private String storePath;

	@Value("${gallery-board.picture.path}")
	private String path;

	@Value("${gallery-board.picture.deleted-path}")
	private String deletedPath;

	@Value("${gallery-board.picture.volume}")
	private Long volume;

	@Value("${gallery-board.picture.max-file-count}")
	private int maxFileCount;

	public GalleryFileServiceImpl(FileMapper fileMapper, ApplicationEventPublisher eventPublisher) {
		super(fileMapper, eventPublisher);
	}

	/** DB에 저장된 상대 경로를 물리 파일 접근용 절대 경로로 변환한다. */
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

	/** 갤러리 이미지 최대 용량(바이트)을 반환한다. */
	@Override
	protected Long getFileMaxSize() {
		return volume;
	}

	/** 갤러리에서 허용되는 이미지 MIME 서브타입 목록을 반환한다. */
	@Override
	protected Set<String> getFileTypes() {
		return FileTypes.galleryFileTypes();
	}

	@Override
	protected int getMaxFileCount() {
		return maxFileCount;
	}
}
